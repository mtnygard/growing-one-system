(ns gos.repl
  (:refer-clojure :exclude [eval print read])
  (:require [clojure.pprint :as pp]
            [clojure.string :as str]
            [gos.db :as db]
            [gos.world :as world]))

(defn- prompt
  []
  (printf " => ")
  (flush))

(defn- read
  []
  (prompt)
  (let [line (read-line)]
    (if (or (nil? line) (= ":quit" line))
      ::quit
      line)))

(defn- print
  [x]
  (cond
    (and (map? x) (contains? x :response)) (pp/pprint (:response x))
    (and (map? x) (contains? x :problems)) (pp/pprint (:problems x))
    :else                                  (pp/pprint x)))

(defn- eval
  [db x]
  (world/process (world/current-state db {}) x))

(defn has-command? [buf]
  (str/index-of buf ";"))

(defn first-command [buf]
  (let [split-point (inc (has-command? buf))]
    [(subs buf 0 split-point) (subs buf split-point)]))

(defn run
  []
  (loop [db    (db/classic "datomic:mem://repl")
         accum ""]
    (if (has-command? accum)
      (let [[command remainder] (first-command accum)]
        (when-not (= ::quit command)
          (let [v (eval db command)]
            (print v)
            (recur db remainder))))
      (let [l (read)]
        (when-not (= ::quit l)
          (recur db (str accum " " l)))))))

(defn -main [& args]
  (run)
  (shutdown-agents))
