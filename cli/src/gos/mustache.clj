(ns gos.mustache
  (:require [cljstache.core :refer [render]]
            [clojure.spec-alpha2 :as s]
            [gos.spec-print :as sprint]))

(s/def ::mustache-schema (s/schema [::mustache]))
(s/def ::mustache-result (s/select ::mustache-schema [:mustache]))

(sprint/use ::mustache-result
  (fn [{:keys [mustache] :as data}]
    (let [data (dissoc data :mustache)]
      (print (render mustache data)))))
