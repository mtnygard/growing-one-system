(ns gos.map
  (:require [clojure.walk :as walk]))

(defn anywhere?
  "Returns true if predicate p is true for any element anywhere in the data structure."
  [p coll]
  (some p (nested-map-tree coll)))
