(ns gos.date
  (:import java.text.SimpleDateFormat
           java.text.DateFormat))

(defn- formatter [pat] (SimpleDateFormat. pat))

(defn- parse-with [^DateFormat fmt s] (.parse fmt s))

(defn- mkfmt [pat] (partial parse-with (formatter pat)))

(def yyyy-mm-dd (mkfmt "yyyy-MM-dd"))
