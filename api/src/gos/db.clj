(ns gos.db
  (:require [datomic.client.api :as dclient]
            [datomic.api :as dclassic]
            [io.pedestal.interceptor :as i]
            [datomic.client.api :as client]
            [fern :as f]
            [gos.db :as db]
            [clojure.edn :as edn]))

;; Kernel attributes

(def relation-attributes
  [{:db/ident       :relation/ordered-attributes
    :db/valueType   :db.type/tuple
    :db/tupleType   :db.type/keyword
    :db/cardinality :db.cardinality/one
    :db/doc         "Defines the order of attributes on a relation"}
   {:db/ident       :entity/relation
    :db/valueType   :db.type/ref
    :db/cardinality :db.cardinality/one
    :db/doc         "Backreference from an entity to the relation it is part of."}])

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
        _      (client/create-database client {:db-name db-name})
        conn   (client/connect client {:db-name db-name})
        _      @(client/transact conn {:tx-data relation-attributes})]
    (->DClient client conn)))

(defrecord DClassic [uri conn]
  Tx
  (transact [this tx-data]
    (dclassic/transact conn tx-data))
  Db
  (db [this]
    (dclassic/db conn))
  Q
  (q [this query args]
    (let [result (apply dclassic/q query (db this) (list* args))]
      (println 'query query (db this) (list* args) '=> result)
      result))
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

;; API

(defn mkattr [dbadapter nm ty card & options]
  {:pre [(keyword? nm) (keyword? ty) (keyword? card)]}
  (if (attribute-exists? dbadapter nm)
    (update-attribute nm ty card)
    (define-attribute nm ty card)))

(defn mkrel [dbadapter nm attr-nms]
  {:pre [(keyword? nm) (every? keyword? attr-nms)]}
  {:db/ident                    nm
   :db.entity/attrs             (vec attr-nms)
   :relation/ordered-attributes (vec attr-nms)})

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
