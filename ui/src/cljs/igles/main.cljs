(ns igles.main
  (:require [igles.lib :as lib]
            [re-frame.core :as rf]
            [reagent.core :as reagent]
            [day8.re-frame.http-fx]
            [igles.routes :as routes]
            [igles.views :as views]
            [igles.events]
            [igles.subs]))

;; Debugging aids
(enable-console-print!)

(defn mount-root []
  (reagent/render [views/main-view]
                  (.getElementById js/document "app")))

(defn initialize-scroll-listener []
  (.addEventListener js/window "scroll" #(rf/dispatch [:window-scrolled %])))

(defn ^:export init []
  (println "[main]: loading")
  (rf/dispatch-sync [:initialize-db])
  (rf/dispatch-sync [:initialize-browser-navigation (.-pathname  (.-location js/window))])
  (initialize-scroll-listener)
  (mount-root))

(defn ^:export reload!
  []
  (mount-root))
