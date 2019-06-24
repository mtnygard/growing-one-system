(ns gos.datomic-fixtures
  (:require  [clojure.test :as t]
             [datomic.api :as d])
  (:import [java.util UUID]))

(defn new-database
  "This generates a new, empty Datomic database for use within unit tests."
  [txes]
  (let [uri  (str "datomic:mem://test" (UUID/randomUUID))
        _    (d/create-database uri)
        conn (d/connect uri)]
    (doseq [t txes]
      @(d/transact conn t))
    {:uri uri
     :connection conn}))

(def ^:dynamic *current-db-connection* nil)
(def ^:dynamic *current-db-uri* nil)

(defmacro with-database
  "Executes all requests in the body with the same database."
  [txes & body]
  `(let [dbm# (new-database ~txes)]
     (binding [*current-db-uri* (:uri dbm#)
               *current-db-connection* (:connection dbm#)]
       ~@body)))

(defn connection
  ([]
   (or *current-db-connection* (:connection (new-database []))))
  ([txes]
   (or *current-db-connection* (:connection (new-database txes)))))

(defn lookup-attribute
  ([db-ident]
   (lookup-attribute (d/db *current-db-connection*) db-ident))
  ([db db-ident]
   (d/pull db '[*] [:db/ident db-ident])))
