(ns gos.debug)

(def ^:dynamic *debug-query* nil)
(def ^:dynamic *debug-transaction* nil)
(def ^:dynamic *debug-evaluation* nil)

(defn indent-print [{:keys [depth]} m]
  (dotimes [_ (or depth 0)] (print ".."))
  (println m))

(def safe-inc (fnil inc 0))
(def safe-dec (fnil dec 0))

(defn indent [x] (update x :depth safe-inc))
(defn dedent [x] (update x :depth safe-dec))
