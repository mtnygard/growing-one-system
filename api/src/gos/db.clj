(ns gos.db
  (:require [datomic.client.api :as dclient]
            [datomic.api :as dclassic]
            [io.pedestal.interceptor :as i]
            [datomic.client.api :as client]
            [fern :as f]))

;; Paper over API differences between cloud and on-prem

(defprotocol Init
  (init [config] "Returns a context"))

(defprotocol Db
  (db [this] "Return a DB value"))

(defprotocol Tx
  (transact [context tx-data] "Apply a transaction, return a tx result"))

(defprotocol Q
  (q [context query & args]))

(defrecord DClient [client conn]
  Tx
  (transact [this tx-data]
    (dclient/transact conn {:tx-data tx-data}))
  Db
  (db [this]
    (dclient/db conn))
  Q
  (q [this query & args]
    (apply dclient/q query args)))

(defn client [config db-name]
  (let [client (dclient/client config)
        _      (client/create-database client {:db-name db-name})
        conn   (client/connect client {:db-name db-name})]
    (->DClient client conn)))

(defrecord DClassic [uri conn]
  Tx
  (transact [this tx-data]
    (dclassic/transact conn tx-data))
  Db
  (db [this]
    (dclassic/db conn))
  Q
  (q [this query & args]
    (apply dclassic/q query args)))

(defn classic
  ([uri]
    (let [_    (dclassic/create-database uri)
          conn (dclassic/connect uri)]
      (classic uri conn)))
  ([uri conn]
   (->DClassic uri conn)))

;; API

(defn mkattr [nm ty card & options]
  {:pre [(keyword? nm) (keyword? ty) (keyword? card)]}
  {:db/ident       (keyword nm)
   :db/valueType   (keyword "db.type" (name ty))
   :db/cardinality (keyword "db.cardinality" (name card))})

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
