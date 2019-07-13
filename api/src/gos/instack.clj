(ns gos.instack
  (:refer-clojure :exclude [read-line])
  (:import clojure.lang.LineNumberingPushbackReader))

(defn read-line [source]
  (let [reader (:reader source)
        nm  (:source source)
        n   (.getLineNumber ^LineNumberingPushbackReader reader)
        l   (.readLine reader)]
    {:source      nm
     :line-number n
     :line        l}))

(defn read-lines [source]
  (lazy-seq
    (let [l (read-line source)]
      (println "read-lines: " l)
      (when (some? (:line l))
        (cons l (read-lines source))))))

(defn lines [sources]
  (mapcat read-lines sources))

(defn mksource [nm reader]
  {:source nm :reader reader})
