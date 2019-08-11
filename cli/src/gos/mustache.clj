(ns gos.mustache
  (:require [cljstache.core :refer [render]]
            [clojure.spec-alpha2 :as s]
            [gos.spec-print :as sprint]))

(s/def ::mustache-schema (s/schema [::mustache]))
(s/def ::mustache-result (s/select ::mustache-schema [:mustache]))

(s/def ::query-result set?)
(s/def ::query-fields sequential?)
(s/def ::query-schema (s/schema [::query-result ::query-fields]))
(s/def ::template-in-query (s/select ::query-schema [:query-result {::query-result set?}]))

(defn template-in-query? [m]
  (s/valid? ::template-in-query m))

(defn extract-template [m]
  (-> m :query-result ffirst))

(sprint/use ::mustache-result
  (fn [{:keys [mustache] :as data}]
    (let [data (dissoc data :mustache)
          mustache (if (template-in-query? mustache) (extract-template mustache) mustache)]
      (print (render mustache data)))))
