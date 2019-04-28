(ns igles.util
  (:require [re-frame.core :as rf]))

(def   <sub (comp deref rf/subscribe vector))

;; >dis just dispatches its arguments as an event
(def   >dis (comp rf/dispatch vector))

;; >evt returns an event handler that dispatches a re-frame event with
;; the js event as the 2nd argument.
(defn- >evt [x & args] (fn [& [evt]] (rf/dispatch (apply vector x evt args))))
