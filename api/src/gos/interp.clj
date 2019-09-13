(ns gos.interp
  (:refer-clojure :exclude [find map?]))

(defmacro defp [nm & decls]
  (list* `def (with-meta nm (assoc (meta nm) :private true)) decls))

;; Interpret commands in the style of a Lisp. Each command is a list
;; of expressions. The first one is either a "function" or a "macro".
;; The difference is whether the forms are evaluated prior to invoking
;; the code bound to the name of the first form.

;; The "environment" hold the state of a virtual machine.
(defp empty-environment {:symtab {} :stack [] :error-stack []})

;; Names are bound in an environment
(defn- bind [s v env] (assoc-in env [:symtab s] v))
(defn- find [s env]   (get-in env [:symtab s]))

;; The stack records where we are executing
(defn- push-frame [fr env] (update-in env [:stack] conj fr))
(defn- pop-frame  [env]    (update-in env [:stack] pop))

;; A name is a symbol
(defp str->name symbol)
(defp name->str name)
(defp name?     symbol?)

;; A frame consists of a name, a possibly-empty argument list, and an
;; node (which identifies the source location)
(defn- mkframe [name args node] {:name name :args args :node node})

;; A stack trace is just the current stack of frames from the
;; environment
(defp stacktrace :stack)

;; "Raising" an error means we stop evaluating
(defn- push-error [err env] (update-in env [:error-stack] conj err))
(defn- clear-error [env]    (assoc-in env [:error-stack] []))
(defn- raise [err env]      (push-error {:cause err :at (stacktrace env)} env))
(defp  errors :error-stack)

;; Evaluating a form is polymorphic
(defprotocol Eval
  (frame [this]        "Return a stack frame describing this form")
  (evaluate [this env] "Evaluation depends on what the first expression is. Return pair of value and new env"))

;; Interpretation begins with a top-level form and an environment.
;; The environment probably contains bindings from previous forms.
(defn- interpret [top env]
  (let [env (push-frame (frame top) env)]
    (let [[val env] (evaluate top env)]
      (if (not-empty (errors env))
        [nil env]
        [val (pop-frame env)]))))

;; A symbol looks itself up in the environment
(defrecord Symbol [s ast]
  Eval
  (frame [this]
    (mkframe 'Symbol nil ast))

  (evaluate [this env]
    (if-let [v (find s env)]
      [v env]
      [nil (raise (str "Symbol " s " is not defined") env)])))

(defrecord Literal [val ast]
  Eval
  (frame [this]
    (mkframe 'Literal nil ast))

  (evaluate [this env]
    [val env]))

(defn- eval-vec [xs env]
  (reduce
    (fn [[vals env] f]
      (let [[v e] (evaluate f env)]
        [(conj vals v) e]))
    [[] env]
    xs))

(defrecord Map [kvs ast]
  Eval
  (frame [this]
    (mkframe 'Map kvs ast))

  (evaluate [this env]
    (let [env      (push-frame (frame this) env)
          [vs env] (eval-vec kvs env)]
      [(apply hash-map vs) (pop-frame env)])))

(defn- fcall [f args env]
  [(apply f args) env])

(defrecord Statement [hd tl ast]
  Eval
  (frame [this] (mkframe hd tl ast))
  (evaluate [this env]
    (let [fr   (frame this)
          env  (push-frame fr env)
          body (find hd env)]
      (if-not body
        [nil env]
        (let [[args env] (eval-vec tl env)
              [v env]    (fcall body args env)]
          [v (pop-frame env)])))))




(comment

  (interpret (Map. [(Symbol. 'ofo {}) (Symbol. 'bar {})] {})
    empty-environment)

  (->> empty-environment
    (bind 'foo :a))

  (->> empty-environment
    (bind 'foo :a)
    (interpret (Symbol. 'foo {})))


  (->> empty-environment
    (bind 'foo :b)
    (interpret (Map. [(Symbol. 'foo {}) (Literal. 10 {})
                      (Literal. :a {})  (Literal. "this is the day" {})]
                 {})))

  (->> empty-environment
    (bind 'print println)
    (bind 'foo :b)
    (interpret
      (Statement. 'print
        [(Map. [(Symbol. 'foo {}) (Literal. 10 {})
                 (Literal. :a {})  (Literal. "this is the day" {})]
            {})]
        {})))


  )
