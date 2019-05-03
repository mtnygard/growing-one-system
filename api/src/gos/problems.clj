(ns gos.problems)

(defmacro and-then->
  "When (:problems expr) is falsey, threads expr into the first form (via ->),
  and when that result has no problems, through the next etc. At any
  point where (:problems expr) is truthy, returns the last value of
  the expr.

  Compare to some->."
  [expr & forms]
  (let [g     (gensym)
        steps (map (fn [step] `(if (:problems ~g) ~g (-> ~g ~step)))
                   forms)]
    `(let [~g ~expr
           ~@(interleave (repeat g) (butlast steps))]
       ~(if (empty? steps)
          g
          (last steps)))))

(def intov (fnil into []))

(defn with-problems
  [ctx & problems]
  (update ctx :problems intov problems))

(defn or-else
  [ctx test problem]
  (if (test ctx)
    ctx
    (with-problems ctx problem)))