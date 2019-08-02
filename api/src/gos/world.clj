(ns gos.world
  (:require [clojure.string :as str]
            [gos.db :as db]
            [gos.problems :refer [and-then-> with-problems with-exception]]
            [gos.responses :as responses :refer [bad-request ok]]
            [gos.seq :refer [conjv sequential-tree]]
            [instaparse.core :as insta]
            [io.pedestal.interceptor :as i]
            [clojure.edn :as edn]
            [gos.date :as date]))

(defn relation [dbadapter nm] (db/rel dbadapter nm))
(def  relation-attributes :relation/ordered-attributes)
(defn relation? [e]      (contains? e relation-attributes))

;; Querying
(defn- lvar? [x]
  (and (symbol? x) (str/starts-with? (name x) "?")))

(defn- genlvar
  ([] (gensym "?"))
  ([prefix] (gensym (str "?" prefix))))

(defn- k->lv [kw]
  (genlvar (name kw)))

(defn- lparms [attrs pat]
  (mapv #(if (lvar? %2) %2 (k->lv %1)) attrs pat))

(defn- lclause [esym attrs lv]
  (mapv vector (repeat esym) attrs lv))

(defn- mask [pred rvals maskvals]
  (map #(if-not (pred %1 %2) %1) rvals maskvals))

(defn- in-clause [findvars pattern]
  (keep identity (mask #(lvar? %2) findvars pattern)))

(def empty-query
  {:find  []
   :in    ['$]
   :where []
   :args  []})

(defn- merge-query
  [q x]
  (-> q
    (update :find  concat (remove (set (:find q)) (:find x)))
    (update :in    concat (:in x))
    (update :where concat (:where x))
    (update :args  concat (:args x))))

(defn- run-query
  [dbadapter q]
  (db/q dbadapter (dissoc q :args) (:args q)))

(defmulti build-datalog (fn [_ [op & _]] op))

(defmethod build-datalog ::relation
  [dbadapter [_ relnm pattern]]
  (let [rel   (relation dbadapter relnm)
        attrs (relation-attributes rel)
        ident (:db/ident rel)
        lv    (lparms attrs pattern)
        esym  (gensym "?e")
        where (lclause esym attrs lv)]
    {:find  (into [] (distinct lv))
     :in    (in-clause lv pattern)
     :where (into [[esym :entity/relation ident]] where)
     :args  (remove lvar? pattern)}))

(defmethod build-datalog ::operator
  [dbadapter [_ & pat]]
  {:where [[(list* pat)]]})

;; this form will be used when a query is parsed from text.
(defn query-relations [dbadapter xs]
  (let [query (reduce merge-query empty-query (map #(build-datalog dbadapter %) xs))]
    {:query-result (run-query dbadapter query)
     :query-fields (:find query)}))

(def query-helper-fn
  (memoize
    (fn [dbadapter relnm]
      (fn [pattern]
        (:query-result (query-relations dbadapter [[::relation relnm pattern]]))))))

;; Sanity checks
(defn assert-has-attributes [nm attrs]
  (assert
    (not (empty? attrs))
    (str "Not a relation: " nm)))

(defn assert-sufficient-values [nm attrs vals]
  (assert (= (count attrs) (count vals))
    (str
      "Relation " nm " has " (count attrs)
      " attributes but " (count vals) " values were supplied.")))

(defn assert-sufficient-pattern [nm attrs pattern]
  (assert (= (count attrs) (count pattern))
    (str
      "Relation " nm " has " (count attrs)
      " attributes but the query pattern has " (count pattern) " markers.")))

(defn assert-attribute-exists [attrnm actualtype]
  (assert (some? actualtype)
    (str "Attribute " attrnm " does not exist.")))

;; From AST to effects

(defn- has-lvars?        [x] (some lvar? (sequential-tree x)))

(defn- classify-command [[a & body]]
  (cond
    (has-lvars? body) :query
    (= :instance a)   :instance
    :else             a))

(defmulti ->effect
  (fn [_ command] (classify-command command)))

(defmethod ->effect :attribute [state [_ nm ty card]]
  (update state :tx-data conjv (db/mkattr (:dbadapter state) nm ty card)))

(defmethod ->effect :relation [state [_ nm attr-nms]]
  (update state :tx-data concat (db/mkrel (:dbadapter state) nm attr-nms)))

;; if :repeat appears in the vals vector, hold the stuff to the left
;; constant, and chunk the stuff on the right as needed to fill the #
;; attrs
(defn- left-of [v xs]
  (take-while #(not= v %) xs))

(defn- right-of [v xs]
  (next (drop-while #(not= v %) xs)))

(defn unpack-repeats
  [width vals]
  (let [constant-part  (left-of :repeat vals)
        foreach-part   (right-of :repeat vals)
        missing-values (- width (count constant-part))
        leftover       (if (= 0 missing-values) 0 (rem (count foreach-part) missing-values))]
    (if (empty? foreach-part)
      [constant-part]
      (do
        (assert (= 0 leftover) (str "Not enough values, need a multiple of " missing-values ". Had " leftover " extra values."))
        (map concat
          (repeat constant-part)
          (partition-all missing-values (right-of :repeat vals)))))))

(defmethod ->effect :instances [{:keys [dbadapter] :as state} [_ ivals]]
  (update state :tx-data conjv
    (doall
      (for [[nm & vals] ivals
            :let        [rel   (relation dbadapter nm)
                         attrs (relation-attributes rel)
                         _     (assert-has-attributes nm attrs)]
            vals        (unpack-repeats (count attrs) vals)]
        (do
          (assert-sufficient-values nm attrs vals)
          (db/mkent dbadapter nm attrs vals))))))

(defmulti query-clause (fn [_ [op & _]] (if (vector? op) (first op) op)))

(defmethod query-clause ::operator
  [_ [x & xs]]
  (into x xs))

(defmethod query-clause :default
  [{:keys [dbadapter] :as state} [nm & pattern]]
  (let [attrs (relation-attributes (relation dbadapter nm))]
    (assert-has-attributes nm attrs)
    (assert-sufficient-pattern nm attrs pattern)
    [::relation nm pattern]))

(defmethod ->effect :query [state [_ clauses]]
  (assoc state :query (mapv #(query-clause state %) clauses)))

;; Parsing inputs

(def ^:private whitespace
  (insta/parser
   "whitespace = #'\\s+'"))

(def whitespace-or-comments
  (insta/parser
   "ws-or-comments = #'\\s+' | comments
    comments = comment+
    comment = '//' inside-comment* '\n'
    inside-comment =  !( '\n' | '//' ) #'.' | comment"
   :auto-whitespace whitespace))

(def ^:private grammar
  (insta/parser
   "<input>          = ((attribute / relation / statements) <';'>)*
    attribute        = <'attr'> name type cardinality
    relation         = 'relation' name attrref+
    statements       = statement ( <','> statement )*
    statement        = (name / operator) repeat? value ( repeat? value)*
    repeat           = <':'>
    <attrref>        = name | constrained-name
    constrained-name = <'('> name constraint <')'>
    constraint       = 'in' name
    name             = #\"[a-zA-Z_][a-zA-Z0-9_\\-\\?]*\"
    type             = #\"[a-zA-Z_][a-zA-Z0-9]*\"
    value            = symbol | string-literal | long-literal | boolean-literal | date-literal
    symbol           = #\"[a-zA_Z_0-9\\?][a-zA_Z_0-9\\?\\-\\$%*]*\"
    string-literal   = #\"\\\"(\\.|[^\\\"])*\\\"\"
    long-literal     = #\"-?[0-9]+\"
    date-literal     = #\"[0-9]{4}-[0-9]{2}-[0-9]{2}\"
    boolean-literal  = 'true' | 'false'
    cardinality      = 'one' | 'many'
    operator         = '=' | '!=' | '<' | '<=' | '>' | '>='"
   :auto-whitespace whitespace-or-comments))

(defn- statements [& vs]
  (if (has-lvars? vs)
    [:query vs]
    [:instances vs]))

(defn- transform [parse-tree]
  (insta/transform
    {:attribute        (fn [n t c] [:attribute n t c])
     :name             keyword
     :type             keyword
     :cardinality      keyword
     :value            identity
     :symbol           symbol
     :constraint       (fn [x & more] (list* (keyword x) more))
     :constrained-name vector
     :string-literal   edn/read-string
     :long-literal     edn/read-string
     :boolean-literal  edn/read-string
     :date-literal     date/yyyy-mm-dd
     :statement        vector
     :statements       statements
     :repeat           (constantly :repeat)
     :relation         (fn [_ r & xs] [:relation r xs])
     :operator         (fn [x] [::operator (symbol x)])}
   parse-tree))

;; Processing a request

(defn initial-state [dbadapter]
  {:dbadapter dbadapter
   :print     true})

(defn with-input [state input]
  (assoc state :body input))

(defn cleanse [state]
  (dissoc state :response :tx-data :parsed :tx-result :query-result :problems :query :query-fields))

(defn parse [{:keys [body] :as state}]
  (let [result (insta/parse grammar body)]
    (if (insta/failure? result)
      (with-problems state (insta/get-failure result))
      (assoc state :parsed (transform result)))))

(defmacro catching [& body]
  `(try
     ~@body
     (catch Throwable t#
       (with-exception ~'state t#))))

(defn determine-effects [state]
  (catching
    (reduce ->effect state (:parsed state))))

(defn answer-queries [{:keys [dbadapter query] :as state}]
  (catching
    (if (some? query)
      (merge state (query-relations dbadapter query))
      state)))

;; todo - consider: can re-frame be used on server side?
(defn apply-transactions [{:keys [tx-data] :as state}]
  (catching
    (cond-> state
      (some? tx-data)
      (assoc :tx-result (mapv #(update
                                 (deref (db/transact (:dbadapter state) %))
                                 :tx-data
                                 seq)
                          tx-data)))))

(defn response [state]
  (assoc state :response (ok (select-keys state [:problems :tx-result :query-result :query-fields]))))

(defn process
  [start-state]
  (and-then-> (cleanse start-state)
    (parse)
    (determine-effects)
    (answer-queries)
    (apply-transactions)
    (response)))

;; Interface for Pedestal
(def accept
  (i/interceptor
   {:name ::accept
    :enter (fn [{:keys [request] :as ctx}]
             (let [{:keys [conn dbadapter]} request
                   world             (-> request :path-params :world)
                   body              (first (vals (select-keys request [:transit-params :json-params :edn-params])))]
               (assoc ctx :response (process (with-input (initial-state dbadapter) body)))))}))
