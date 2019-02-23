(ns gos.main
  (:require [com.cognitect.vase.fern :as vf]))

(defn -main
  [filename & _]
  (vf/server filename))
