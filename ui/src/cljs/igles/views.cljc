(ns igles.views)

(defmacro handle
  ([& body]
   `(fn [~'event] ~@body nil)))

