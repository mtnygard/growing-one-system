(ns gos.world
  (:require [clojure.string :as str]
            [gos.db :as db]
            [gos.problems :refer [and-then-> with-problems]]
            [gos.responses :as responses :refer [bad-request ok]]
            [gos.seq :refer [conjv sequential-tree]]
            [instaparse.core :as insta]
            [io.pedestal.interceptor :as i]
            [clojure.edn :as edn]))

(defn relation [dbadapter nm] (db/e dbadapter nm))
(def relation-attributes :relation/ordered-attributes)

;; Querying

(defn- lvar [x]
  (cond
    (and (string? x) (str/starts-with? x "?")) (symbol x)
    (string? x)                                (lvar (str "?" x))
    (keyword? x)                               (lvar (name x))
    (symbol? x)                                x
    :else                                      (lvar (str "?" x))))

(defn- lvar? [x]
  (and (symbol? x) (str/starts-with? (name x) "?")))

(defn- k->lv [kw]
  (gensym (str "?" (name kw))))

(defn- mask [pred rvals maskvals]
  (map #(if-not (pred %1 %2) %1) rvals maskvals))

(defn- lparms [attrs pat]
  (mapv #(if (lvar? %2) %2 (k->lv %1)) attrs pat))

(defn- lparmvals [pat]
  (remove lvar? pat))

(defn- lclause [esym attrs lv]
  (mapv vector (repeat esym) attrs lv))

(defn- find-clause [attrs pattern]
  (mapv #(if (lvar? %2) %2 (k->lv %1)) attrs pattern))

(defn- in-clause [findvars pattern]
  (into ['$] (keep identity (mask #(lvar? %2) findvars pattern))))

(defn- build-query [{:keys [db/ident relation/ordered-attributes] :as reln} pattern]
  (let [lv    (lparms ordered-attributes pattern)
        esym  (gensym "?e")
        where (lclause esym ordered-attributes lv)]
    {:find  (into [] lv)
     :in    (in-clause lv pattern)
     :where (into [[esym :entity/relation ident]] where)}))

(defn query-args [pattern]
  (into [] (lparmvals pattern)))

(defn query-relation [dbadapter rel pattern]
  (let [rel (relation dbadapter rel)]
    (db/q dbadapter
      (build-query rel pattern)
      (query-args pattern))))

(defn query-helper-fn [rel]
  (fn [dbadapter & pattern]
    (apply query-relation dbadapter rel pattern)))

;; Sanity checks

(defn relation? [e]
  (contains? e :relation/ordered-attributes))

(defn assert-has-attributes [nm attrs]
  (assert
    (not (empty? attrs))
    (str "Not a relation: " nm)))

(defn assert-sufficient-values [nm attrs vals]
  (assert (= (count attrs) (count vals))
    (str
      "Relation " nm " has " (count attrs)
      " attributes but " (count vals) " values were supplied.")))

(defn assert-attribute-exists [attrnm actualtype]
  (assert (some? actualtype)
    (str "Attribute " attrnm " does not exist.")))

;; Type coercion

(defmulti type-coerce (fn [a _] a))

(defmethod type-coerce :db.type/string
  [_ s] s)

(defmethod type-coerce :db.type/long
  [_ s]
  (edn/read-string s))

(defmethod type-coerce :db.type/symbol
  [_ s]
  (symbol s))

(defmethod type-coerce :db.type/boolean
  [_ s]
  (some? (edn/read-string s)))

(defmethod type-coerce :db.type/double
  [_ s]
  (double (edn/read-string s)))

(defn coerce [dbadapter attr strval]
  (let [dbtype (db/attr-type dbadapter attr)]
    (assert-attribute-exists attr dbtype)
    (type-coerce dbtype strval)))

;; Implementation

(defn- has-lvars? [x] (some lvar? (sequential-tree x)))

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
  (update state :tx-data conjv (db/mkrel (:dbadapter state) nm attr-nms)))

(defmethod ->effect :instance [state [_ nm vals]]
  (let [attrs (relation-attributes (relation (:dbadapter state) nm))]
    (assert-has-attributes nm attrs)
    (assert-sufficient-values nm attrs vals)
    ;; TODO - coercion goes here
    (let [vals (map #(coerce (:dbadapter state) %1 %2) attrs vals)]
      (update state :tx-data conjv (db/mkent (:dbadapter state) nm attrs vals)))))

(defmethod ->effect :query [state [_ & clauses]]
  (assoc state :query clauses))

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
   "<input> = ((attribute / relation / instance) <';'>)*
     attribute = <'attr'> name type cardinality
     relation = 'relation' name name+
     instance = name (lvar / value)+
     name = #\"[a-zA-Z_][a-zA-Z0-9_\\-\\?]*\"
     lvar = #\"\\?[a-zA-Z0-9_\\-\\?]*\"
     type = #\"[a-zA-Z_][a-zA-Z0-9]*\"
     value = #\"[^\\s;]+\"
     cardinality = 'one' | 'many'"
   :auto-whitespace whitespace-or-comments))

(defn- transform [parse-tree]
  (insta/transform
    {:attribute   (fn [n t c] [:attribute n t c])
     :name        keyword
     :lvar        symbol
     :type        keyword
     :cardinality keyword
     :value       identity
     :instance    (fn [r & vs] [:instance r vs])
     :relation    (fn [_ r & xs] [:relation r xs])}
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

(defn answer-queries [{:keys [query] :as state}]
  (when query
    (println '+++++)
    (println (apply query-relation (:dbadapter state) query))
    (println '+++++))
  (cond-> state
    (some? query)
    (assoc :query-result (apply query-relation (:dbadapter state) query))))

;; todo - consider: can re-frame be used on server side?
(defn apply-transactions [{:keys [tx-data] :as state}]
  (cond-> state
    (some? tx-data)
    (assoc :tx-result @(db/transact (:dbadapter state) tx-data))))

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

;; Interface

(def accept
  (i/interceptor
   {:name ::accept
    :enter (fn [{:keys [request] :as ctx}]
             (let [{:keys [conn dbadapter]} request
                   world             (-> request :path-params :world)
                   body              (first (vals (select-keys request [:transit-params :json-params :edn-params])))]
               (assoc ctx :response (process (current-state dbadapter world) body))))}))
