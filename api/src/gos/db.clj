(ns gos.db
  (:require [datomic.client.api :as d]
   [io.pedestal.interceptor :as i]))

(def transact-context
  (i/interceptor
   {:name  ::transact-context
    :enter (fn [{:keys [request]
                 :as   ctx}]
             (if (contains? ctx :tx-data)
               (assoc ctx :tx-result (d/transact (:conn request) (select-keys ctx [:tx-data])))
               ctx))
    :leave (fn [{:keys [tx-result]
                 :as   ctx}]
             (cond-> ctx
               (some? (:tx-result ctx))
               (update :tx-result deref)))}))