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
(defn- bind [env s v] (assoc-in env [:symtab s] v))
(defn- find [env s]   (get-in env [:symtab s]))

;; The stack records where we are executing
(defn- push-frame [env fr] (update-in env [:stack] conj fr))
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
(defn- push-error [env err] (update-in env [:error-stack] conj err))
(defn- clear-error [env]    (assoc-in env [:error-stack] []))
(defn- raise [env err]      (push-error env {:cause err :at (stacktrace env)}))
(defp  errors :error-stack)

;; Evaluating a form is polymorphic
(defprotocol Eval
  (frame [this]        "Return a stack frame describing this form")
  (evaluate [this env] "Evaluation depends on what the first expression is"))

;; Interpretation begins with a top-level form and an environment.
;; The environment probably contains bindings from previous forms.
(defn- interpret [env top]
  (let [env (push-frame env (frame top))]
    (let [[env val] (evaluate top env)]
      (if (not-empty (errors env))
        (errors env)
        [(pop-frame env) val]))))

;; A symbol looks itself up in the environment
(defrecord Symbol [s ast]
  Eval
  (frame [this]
    (mkframe 'Symbol nil ast))

  (evaluate [this env]
    (if-let [v (find env s)]
      [env v]
      [(raise env (str "Symbol " s " is not defined")) nil])))

(defrecord Literal [val ast]
  Eval
  (frame [this]
    (mkframe 'Literal nil ast))

  (evaluate [this env]
    [env val]))

(defrecord Map [m ast]
  Eval
  (frame [this]
    (mkframe 'Map nil ast))

  (evaluate [this env]
    (zipmap
      (map #(evaluate % env) (keys m))
      (map #(evaluate % env) (vals m)))))

(defrecord Statement [head tail ast]
  Eval
  (frame [this] (mkframe head tail ast))
  (evaluate [this env]

    ))
