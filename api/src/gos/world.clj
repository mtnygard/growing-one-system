(ns gos.world
  (:require [clojure.string :as str]
            [gos.db :as db]
            [gos.problems :refer [and-then-> with-problems]]
            [gos.responses :as responses :refer [bad-request ok]]
            [gos.seq :refer [conjv]]
            [instaparse.core :as insta]
            [io.pedestal.interceptor :as i]))

;; Implementation

(defmulti ->effect (fn [_ [a & _]] a))

(defmethod ->effect :attribute [state [_ nm ty card]]
  (update state :tx-data conjv (db/mkattr (:dbadapter state) nm ty card)))

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
    :name        keyword
    :type        keyword
    :cardinality keyword
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
;; todo - consider: can re-frame be used on server side?
(defn effect [state]
  (let [state   (reduce ->effect state (:parsed state))
        tx-data (:tx-data state)]
    (assoc state :tx-result @(db/transact (:dbadapter state) tx-data))))

(defn response [state]
  (assoc state :response (ok (select-keys state [:problems :outcome]))))

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
             (let [{:keys [conn dbadapter]} request
                   world             (-> request :path-params :world)
                   body              (first (vals (select-keys request [:transit-params :json-params :edn-params])))]
               (assoc ctx :response (process (current-state dbadapter world) body))))}))
