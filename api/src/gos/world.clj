(ns gos.world
  (:require [clojure.string :as str]
            [gos.db :as db]
            [gos.problems :refer [and-then-> with-problems]]
            [gos.responses :as responses :refer [bad-request ok]]
            [gos.seq :refer [conjv sequential-tree]]
            [instaparse.core :as insta]
            [io.pedestal.interceptor :as i]
            [clojure.edn :as edn]))

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

(defmulti build-datalog
  (fn [_ x] (when (keyword? (first x)) :eq)))

(defmethod build-datalog :eq
  [dbadapter [relnm pattern]]
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

;; this form will be used when a query is parsed from text.
(defn query-relations [dbadapter xs]
  (run-query
    dbadapter
    (reduce
      merge-query
      empty-query
      (map #(build-datalog dbadapter %) xs))))

(def query-helper-fn
  (memoize
    (fn [dbadapter relnm]
      (fn [pattern]
        (query-relations dbadapter [[relnm pattern]])))))

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

(defmethod ->effect :instances [{:keys [dbadapter] :as state} [_ ivals]]
  (update state :tx-data conjv
    (doall
      (for [[nm & vals] ivals
            :let        [attrs (relation-attributes (relation dbadapter nm))]]
        (do
          (assert-has-attributes nm attrs)
          (assert-sufficient-values nm attrs vals)
          (db/mkent dbadapter nm attrs vals))))))

(defmethod ->effect :query [{:keys [dbadapter] :as state} [_ clauses]]
  (assoc state :query
    (doall
      (for [[nm & pattern] clauses
            :let           [attrs (relation-attributes (relation dbadapter nm))]]
        (do
          (assert-has-attributes nm attrs)
          (assert-sufficient-pattern nm attrs pattern)
          [nm pattern])))))

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
   "<input> = ((attribute / relation / statements) <';'>)*
    attribute = <'attr'> name type cardinality
    relation = 'relation' name name+
    statements = statement ( <','> statement )*
    statement = name value+
    name = #\"[a-zA-Z_][a-zA-Z0-9_\\-\\?]*\"
    type = #\"[a-zA-Z_][a-zA-Z0-9]*\"
    value = symbol | string-literal | long-literal | boolean-literal
    symbol = #\"[a-zA_Z_0-9\\?][a-zA_Z_0-9\\?\\-\\$%*]*\"
    string-literal = #\"\\\"(\\.|[^\\\"])*\\\"\"
    long-literal = #\"-?[0-9]+\"
    boolean-literal = 'true' | 'false'
    cardinality = 'one' | 'many'"
   :auto-whitespace whitespace-or-comments))

(defn- statements [& vs]
  (if (has-lvars? vs)
    [:query vs]
    [:instances vs]))

(defn- transform [parse-tree]
  (insta/transform
    {:attribute      (fn [n t c] [:attribute n t c])
     :name           keyword
     :type           keyword
     :cardinality    keyword
     :value          identity
     :symbol         symbol
     :string-literal edn/read-string
     :long-literal   edn/read-string
     :statement      vector
     :statements     statements
     :relation       (fn [_ r & xs] [:relation r xs])}
   parse-tree))

;; Processing a request

(defn current-state [dbadapter world]
  {:dbadapter dbadapter
   :world     world})

(defn parse [state body]
  (let [result (insta/parse grammar body)]
    (if (insta/failure? result)
      (with-problems state (insta/get-failure result))
      (assoc state :parsed (transform result)))))

;; todo - generalize to more than just DB transactions
(defn determine-effects [state]
  (reduce ->effect state (:parsed state)))

(defn answer-queries [{:keys [dbadapter query] :as state}]
  (cond-> state
    (some? query)
    (assoc :query-result (query-relations dbadapter query))))

;; todo - consider: can re-frame be used on server side?
(defn apply-transactions [{:keys [tx-data] :as state}]
  (cond-> state
    (some? tx-data)
    (assoc :tx-result (mapv #(deref (db/transact (:dbadapter state) %)) tx-data))))

(defn response [state]
  (assoc state :response (ok (select-keys state [:problems :tx-result :query-result]))))

(defn process
  [start-state body]
  (and-then-> start-state
    (parse body)
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
               (assoc ctx :response (process (current-state dbadapter world) body))))}))
