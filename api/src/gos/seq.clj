(ns gos.seq)

(defn conjv [coll x]
  (conj (or coll []) x))
