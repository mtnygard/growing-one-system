(ns user
  (:require [io.pedestal.http :as http]
            [gos.main]))

(defonce server (atom nil))

(defn reset []
  (when @server
    (swap! server http/stop))
  (reset! server (gos.main/-main "src/config.fern")))
