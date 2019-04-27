(ns igles.routes
  (:require [clojure.string :as str]
            [goog.events :as gevents]
            [goog.history.EventType :as EventType]
            [igles.util :refer [<sub >dis]]
            [igles.db :as db]
            [reitit.core :as reitit]
            [reitit.frontend :as rfront]
            [re-frame.core :as rf])
  (:import goog.history.Html5History
           goog.history.Html5History.TokenTransformer))

(def routes
  [["/"  {:name :home
          :view :capture}]])

(def router (rfront/router routes))

(defn- url-for
  ([nm]
   (reitit/match->path (rfront/match-by-name router nm)))
  ([nm path-params]
   (reitit/match->path (rfront/match-by-name router nm path-params)))
  ([nm path-params query-params]
   (reitit/match->path (rfront/match-by-name router nm path-params) query-params)))

(def home (partial url-for :home))

(def token-transformer
  (let [t (TokenTransformer.)]
    (set! (.. t -createUrl)
      (fn [token path-prefix location]
        (str path-prefix token)))
    (set! (.. t -retrieveToken)
      (fn [path-prefix location]
        (str (.-pathname location))))
    t))

(defonce history
  (doto (Html5History. js/window token-transformer)
    (.setUseFragment false)
    (.setPathPrefix "")
    (.setEnabled true)))

(rf/reg-fx
  :hook-browser-navigation
  (fn [[_ _]]
    (gevents/listen history
      EventType/NAVIGATE
      (fn [event]
        (let [uri (or (not-empty (str/replace (.-token event) #"^.*#" "")) "/")]
          (println "NAVIGATE" uri)
          (>dis :set-active-route (rfront/match-by-path router uri)))))))

(rf/reg-event-fx
  :initialize-browser-navigation
  (fn [{:keys [db]} [_ initial-uri]]
    (let [match (rfront/match-by-path router initial-uri)]
      (cond-> {:hook-browser-navigation nil}
        (some? match)
        (assoc :db (db/set-route db (into {} match)))))))

(rf/reg-event-fx
  :set-active-route
  (fn [{:keys [db]} [_ match]]
    {:db (db/set-route db (into {} match))}))
