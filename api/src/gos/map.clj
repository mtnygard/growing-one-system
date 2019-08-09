(ns gos.map)

(defn- genmap [kf vf m]
  (with-meta
    (zipmap (map kf (keys m)) (map vf (vals m)))
    (meta m)))

(defn map-keys [f m] (genmap f identity m))
(defn map-vals [f m] (genmap identity f m))
