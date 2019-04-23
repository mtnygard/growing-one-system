(ns user
  (:require [io.pedestal.http :as http]
            [gos.main]))

(defonce server (atom nil))

(defn reset []
  (when (and @server
          (not= :com.cognitect.vase.try/exit @server))
    (swap! server http/stop))
  (reset! server (gos.main/-main "src/config.fern")))
