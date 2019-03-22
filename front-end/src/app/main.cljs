(ns app.main
  (:require [app.lib :as lib]))

(def a 10)

(defonce b 2)

(defn main! []
  (println "[main]: The wonders of workflow")
  (println "[main]: loading"))

(defn reload! []
  (println "[main] reloaded lib:" lib/c lib/d)
  (println "[main] reloaded:" a b))
