(ns igles.api
  (:require [reitit.core :as reitit]
            [reitit.frontend :as rfront]
            [igles.routes :as routes]))

;; TODO - see how to extract this so it's not repeated from the API config.
(def ^:private api-routes
  [["/user/:user/worlds" {:name :user-worlds}]])

(def ^:private api-router (rfront/router api-routes))

(def ^:private url-for
  (partial routes/url-for-router api-router))

(defn user-worlds [username] (url-for :user-worlds {:user username}))