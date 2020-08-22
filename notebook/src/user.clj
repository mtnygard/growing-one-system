(ns user
  (:require [clojupyter.kernel.version :as ver]))

(defn user-ver []
  (ver/version-string-long))
