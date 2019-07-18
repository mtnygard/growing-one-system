(ns gos.db
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [datomic.api :as dclassic]
            [datomic.client.api :as dclient]
            [fern :as f]
            [gos.char :as char]
            [gos.seq :refer [conjv]]
            [io.pedestal.interceptor :as i]))

(def ^:dynamic *debug-query* nil)
(def ^:dynamic *debug-transaction* nil)

;; Kernel attributes

(def relation-attributes
  [{:db/ident       :entity/relation
    :db/valueType   :db.type/ref
    :db/cardinality :db.cardinality/one
    :db/doc         "Backreference from an entity to the relation it is part of."}
   {:db/ident       :relation/attributes
    :db/valueType   :db.type/ref
    :db/cardinality :db.cardinality/many
    :db/doc         "A relation has many attributes. This defines the types."}
   {:db/ident       :attribute/derives
    :db/valueType   :db.type/ref
    :db/cardinality :db.cardinality/one
    :db/doc         "Attribute types are copied on use. This points back to the original."}])

;; Paper over API differences between cloud and on-prem

(defprotocol Db
  (db [this] "Return a DB value"))

(defprotocol Tx
  (transact [context tx-data] "Apply a transaction, return a tx result"))

(defprotocol Q
  (q [context query args])
  (e [context eid]))

(defrecord DClient [client conn]
  Tx
  (transact [this tx-data]
    (dclient/transact conn {:tx-data tx-data}))
  Db
  (db [this]
    (dclient/db conn))
  Q
  (q [this query args]
    (apply dclient/q query (db this) args))
  (e [this eid]
    (dclient/pull (db this) '[*] eid)))

(defn client [config db-name]
  (let [client (dclient/client config)
        _      (dclient/create-database client {:db-name db-name})
        conn   (dclient/connect client {:db-name db-name})
        _      @(dclient/transact conn {:tx-data relation-attributes})]
    (->DClient client conn)))

(defrecord DClassic [uri conn]
  Tx
  (transact [this tx-data]
    (when *debug-transaction*
      (println 'transact tx-data))
    (dclassic/transact conn tx-data))
  Db
  (db [this]
    (dclassic/db conn))
  Q
  (q [this query args]
    (when *debug-query*
      (println 'query query (list* args)))
    (apply dclassic/q query (db this) (list* args)))
  (e [this eid]
    (dclassic/entity (db this) eid)))

(defn classic
  ([uri]
   (let [_    (dclassic/create-database uri)
         conn (dclassic/connect uri)
         _    @(dclassic/transact conn relation-attributes)]
      (classic uri conn)))
  ([uri conn]
   (->DClassic uri conn)))

(defn- attribute-exists? [dbadaptor nm]
  (some? (e dbadaptor nm)))

(defn- attribute-definition [nmkey nm ty card]
  {nmkey           (keyword nm)
   :db/valueType   (keyword "db.type" (name ty))
   :db/cardinality (keyword "db.cardinality" (name card))})

(def ^:private update-attribute (partial attribute-definition :db/id))
(def ^:private define-attribute (partial attribute-definition :db/ident))

(defn- ->map
  "Turn a datomic entity into an ordinary map."
  [e]
  (select-keys e (keys e)))

(defn- attr-names [kw]
  (map #(keyword (name kw) (str "a_" %)) (iterate inc 0)))

(defn rel-attribute? [relnm x]
  (= (name relnm) (namespace x)))

(defn kw->ordinal [kw]
  (let [n (name kw)]
    (if-not (str/starts-with? n "a_")
      nil
      (let [n (subs n 2)]
        (if-not (every? char/digit? n)
          nil
          (Integer/parseInt n))))))

(defn- derive-type
  [dbadapter from to]
  (let [original (->map (e dbadapter from))]
    (merge (dissoc original :db/id)
      {:db/ident          to
       :attribute/derives from})))

(defn- derived-types
  "Make copies of the attributes used in this relation."
  [dbadapter relnm type-nms]
  (mapv #(derive-type dbadapter %1 %2) type-nms (attr-names relnm)))

(defn- relation-entity
  "Construct a Datomic entity to represent the relation itself."
  [nm type-nms]
  {:db/ident nm
   :relation/attributes (->> nm attr-names (take (count type-nms)) set)})

;; API

(defn mkattr [dbadapter nm ty card & options]
  {:pre [(keyword? nm) (keyword? ty) (keyword? card)]}
  (if (attribute-exists? dbadapter nm)
    [(update-attribute nm ty card)]
    [(define-attribute nm ty card)]))

(defn mkrel [dbadapter nm type-nms]
  {:pre [(keyword? nm) (every? keyword? type-nms) (< 0 (count type-nms))]}
  [(derived-types dbadapter nm type-nms)
   [(relation-entity nm type-nms)]])

(defn rel [dbadapter nm]
  (let [r     (->map (e dbadapter nm))
        attrs (sort-by kw->ordinal (:relation/attributes r))]
    (assoc r :relation/ordered-attributes attrs)))

(defn mkent [dbadapter nm attrs vals]
  {:pre [(keyword? nm)]}
  (assoc (zipmap attrs vals)
    :entity/relation nm))

(defn attr-type [dbadapter attr-nm]
  {:pre [(keyword? attr-nm)]}
  (:db/valueType (e dbadapter attr-nm)))

;; Pedestal+Vase integration

(defn mkadapter [config]
  (if (= :cloud (:type config))
    (client (:client-config config) (:db-name config))
    (classic (:uri config))))

;; Nested interceptor. Outer `:enter` fn is called when the
;; API starts up. It returns the inner interceptor which is
;; invoked on request.
(defrecord DbContext [config]
  i/IntoInterceptor
  (-interceptor [_]
    (let [adapter (mkadapter config)]
      (i/map->Interceptor
        {:enter
         (fn [ctx]
           (update ctx :request assoc :dbadapter adapter))}))))

;; Define a new fern "literal" called `gos.db/adapter`. Use instead
;; of `vase.datomic.cloud/client` or `vase.datomic/connection`
(defmethod f/literal 'gos.db/adapter [_ config]
  (->DbContext config))
