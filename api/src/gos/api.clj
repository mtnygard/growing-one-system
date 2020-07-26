(ns gos.api
  (:require [gos.exec :as exec]
            [io.pedestal.interceptor :as i]))

;; Interface for Pedestal
(def accept
  (i/interceptor
   {:name ::accept
    :enter (fn [{:keys [request] :as ctx}]
             (let [{:keys [_ dbadapter]} request
                   body              (first (vals (select-keys request [:transit-params :json-params :edn-params])))]
               (assoc ctx :response (exec/process (exec/with-input (exec/initial-state dbadapter) body)))))}))
