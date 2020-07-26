(ns gos.spec-print
  (:refer-clojure :exclude [print use])
  (:require [clojure.alpha.spec :as s]
            [clojure.alpha.spec.gen :as gen]
            [clojure.pprint :refer [pprint]]))

(def ^:private spec-printers (atom {}))

(defn use [spec-name printer]
  {:pre [(keyword? spec-name) (fn? printer)]}
  (swap! spec-printers assoc spec-name printer))

(defn- matching-printers
  [m v]
  (filter #(s/valid? (key %) v) m))

(defn print
  ([value]
   (print @spec-printers value))
  ([registry value]
   (let [[s f] (first (matching-printers registry value))]
     (if s
       (f (s/conform s value))
       (pprint value)))))
