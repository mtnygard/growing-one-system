(ns gos.world
  (:require [datomic.client.api :as d]
            [gos.responses :as responses :refer [ok bad-request]]
            [gos.problems :refer [and-then-> with-problems]]
            [instaparse.core :as insta]
            [io.pedestal.interceptor :as i]
            [clojure.string :as str]))

;; Implementation

;; Parsing inputs

(def ^:private grammar
  (insta/parser
    "<input> = ((attribute / relation / instance) <';'>)*
     attribute = 'attr' name type cardinality
     relation = 'relation' name name+
     instance = name value+
     name = #\"[a-zA-Z_][a-zA-Z0-9_\\-\\?]*\"
     type = #\"[a-zA-Z_][a-zA-Z0-9]*\"
     value = #\"[^\\s;]+\"
     cardinality = 'one' | 'many'"
    :auto-whitespace :comma
    )) 

(defn- transform [parse-tree]
  (insta/transform
   {:attribute   (fn [_ n t c] [:attribute n t c])
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

(defn effect [state] state)

(defn response [state]
  (ok (select-keys state [:problems :outcome])))

(defn process
  [conn db world body]
  (and-then->
   (current-state conn db world)
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
