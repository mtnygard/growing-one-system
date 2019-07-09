(ns gos.seq)

(defn conjv [coll x]
  (conj (or coll []) x))

(defn sequential-tree [x]
  (tree-seq sequential? identity x))
