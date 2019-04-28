(ns gos.scores
  (:require [gos.responses :as responses :refer [ok bad-request]]
            [io.pedestal.interceptor :as i]))

(def post
  (i/interceptor
    {:name  :gos.scores/post
     :enter (fn [{:keys [request] :as ctx}]
              (if-let [score (-> request :transit-params :score)]
                (assoc ctx :tx-data [{:player/score score}])
                (assoc ctx :response (bad-request "missing body parameter :score"))))}))
