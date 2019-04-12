(ns igles.main
  (:require [igles.lib :as lib]
            [re-frame.core :as rf]
            [reagent.core :as reagent]
            [igles.views :as views]
            [igles.events]
            [igles.subs]))

;; Debugging aids
(enable-console-print!)

(defn mount-root []
  (reagent/render [views/capture-app]
    (.getElementById js/document "app")))

(defn ^:export init []
  (println "[main]: loading")
  (rf/dispatch-sync [:initialize-db])
  (mount-root))

(defn ^:export reload!
  []
  (mount-root))
