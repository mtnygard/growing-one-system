(ns gos.exec
  (:require [gos.parser :as parser]
            [gos.problems :refer [and-then-> with-exception with-problems]]
            [instaparse.core :as insta]

            [gos.ast :as ast]))

;; these fields are only used during processing of one input
(def ^:private transient-fields
  [:response :tx-data :parsed :tx-result :query-result :problems :query :query-fields])

(defn- cleanse [state]
  (apply dissoc state transient-fields))

(defmacro catching [& body]
  `(try
     ~@body
     (catch Throwable t#
       (with-exception ~'state t#))))

(defn- analyze [state] state)

(defn parse [{:keys [body] :as state}]
  (catching
    (assoc state :parsed (parser/parse body))))

(defn- execute [{:keys [parsed] :as state}]
  (assoc state :value
    (ast/evaluate parsed state)))

(defn- respond [state] state)

;; ========================================
;; Public interface

(defn initial-state [dbadapter]
  {:dbadapter dbadapter
   :print     true})

(defn with-input [state input]
  (assoc state :body input))

(defn process
  [start-state]
  (and-then-> (cleanse start-state)
    (parse)
    (analyze)
    (execute)
    (respond)))
