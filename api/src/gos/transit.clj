(ns gos.transit
  (:require [cognitect.transit :as transit])
  (:import java.io.StringReader
           java.io.ByteArrayInputStream))

;; Initial size of a byte buffer for serializing Transit. The buffer
;; will grow if needed.
(def transit-buffer-size 4096)

(defn encode
  "Encode a value for storage or transfer"
  [x]
  (with-open [out (java.io.ByteArrayOutputStream. transit-buffer-size)]
    (let [writer (transit/writer out :json)]
      (transit/write writer x)
      (.toString out))))

(defn decode
  "Re-hydrate a value from storage or transfer"
  [tr]
  (let [reader (transit/reader (ByteArrayInputStream. (.getBytes tr)) :json)]
    (transit/read reader)))
