(ns gos.world
  (:require [clojure.string :as str]
            [datomic.client.api :as d]
            [gos.problems :refer [and-then-> with-problems]]
            [gos.responses :as responses :refer [bad-request ok]]
            [gos.seq :refer [conjv]]
            [instaparse.core :as insta]
            [io.pedestal.interceptor :as i]))

;; Implementation

(defmulti ->tx-data (fn [_ [a & _]] a))

(defmethod ->tx-data :attribute [state [_ nm ty card]]
  (update state :tx-data conjv
    {:db/ident       nm
     :db/valueType   (keyword "db.type" (str ty))
     :db/cardinality (keyword "db.cardinality" (str card))}))

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
     instance = name value+
     name = #\"[a-zA-Z_][a-zA-Z0-9_\\-\\?]*\"
     type = #\"[a-zA-Z_][a-zA-Z0-9]*\"
     value = #\"[^\\s;]+\"
     cardinality = 'one' | 'many'"
   :auto-whitespace whitespace-or-comments))

(defn- transform [parse-tree]
  (insta/transform
   {:attribute   (fn [n t c] [:attribute n t c])
    :name        identity
    :type        keyword
    :cardinality keyword
    :relation    (fn [_ r & xs] [:relation r xs])}
   parse-tree))

;; Processing a request

(defn current-state [conn db world]
  {:conn  conn
   :db    db
   :world world})

(defn parse [state body]
  (let [result (insta/parse grammar body)]
    (if (insta/failure? result)
      (with-problems state (insta/get-failure result))
      (assoc state :parsed (transform result)))))

(defn effect [state]
  (assoc state :tx-data (reduce ->tx-data state (:parsed state))))

(defn response [state]
  (assoc state :response (ok (select-keys state [:problems :outcome :db]))))

(defn process
  [start-state body]
  (and-then-> start-state
   (parse body)
   (effect)
   (response)))

;; Interface

(def accept
  (i/interceptor
   {:name ::accept
    :enter (fn [{:keys [request] :as ctx}]
             (let [{:keys [conn db]} request
                   world             (-> request :path-params :world)
                   body              (first (vals (select-keys request [:transit-params :json-params :edn-params])))]
               (assoc ctx :response (process conn db world body))))}))
