(ns gos.interp
  (:refer-clojure :exclude [find map?]))

(defmacro def- [nm & decls]
  (list* `def (with-meta nm (assoc (meta nm) :private true)) decls))

;; Interpret commands in the style of a Lisp. Each command is a list
;; of expressions. The first one is either a "function" or a "macro".
;; The difference is whether the forms are evaluated prior to invoking
;; the code bound to the name of the first form.

;; The "environment" hold the state of a virtual machine.
(def  empty-environment {:symtab {} :stack [] :error-stack []})

;; Names are bound in an environment
(def- symtab :symtab)
(defn bind [s v env]  (assoc-in env [symtab s] v))
(defn find [s env]    (get-in env [symtab s]))
(defn binds [env svs] (reduce-kv (fn [env s v] (bind s v env)) env svs))

;; The stack records where we are executing
(defn- push-frame [env fr] (update-in env [:stack] conj fr))
(defn- pop-frame  [env]    (update-in env [:stack] pop))

;; A name is a symbol
(def- str->name symbol)
(def- name->str name)
(def- name?     symbol?)

;; A frame consists of a name, a possibly-empty argument list, and an
;; node (which identifies the source location)
(defn- mkframe [name args node] {:name name :args args :node node})

;; A stack trace is just the current stack of frames from the
;; environment
(def stacktrace :stack)

;; "Raising" an error means we stop evaluating
(defn- push-error [err env] (update-in env [:error-stack] conj err))
(defn- clear-error [env]    (assoc-in env [:error-stack] []))
(defn- raise [err env]      (push-error {:cause err :at (stacktrace env)} env))
(def   errors               :error-stack)
(defn  errors? [env]        (not-empty (errors env)))

;; Evaluating a form is polymorphic
(defprotocol Eval
  (frame [this]        "Return a stack frame describing this form")
  (evaluate [this env] "Evaluation depends on what the first expression is. Return pair of value and new env"))

;; "break" means we are raising an error up the stack.
(defn- break  [env] [nil env])

;; Interpretation begins with a top-level form and an environment.
;; The environment probably contains bindings from previous forms.
(defn interpret [top env]
  (if (errors? env)
    (break env)
    (let [env (push-frame env (frame top))]
      (let [[val env] (evaluate top env)]
        (if (errors? env)
          (break env)
          [val (pop-frame env)])))))

;; A symbol looks itself up in the environment
(defrecord Symbol [s ast]
  Eval
  (frame [this]
    (mkframe 'Symbol nil ast))

  (evaluate [this env]
    (if-let [v (find s env)]
      [v env]
      (break (raise (str "Symbol " s " is not defined") env)))))

(def mksym ->Symbol)

(defrecord Literal [val ast]
  Eval
  (frame [this]
    (mkframe 'Literal nil ast))

  (evaluate [this env]
    [val env]))

(def mklit ->Literal)

(defn- eval-vec [xs env]
  (reduce
    (fn [[vals env] f]
      (let [[v e] (interpret f env)]
        [(conj vals v) e]))
    [[] env]
    xs))

;; A closure is a frozen form of the current namespace, with the stack
;; recorded for use in debugging
(defn- closure [env]
  {:symtab         (symtab env)
   :captured-stack (stacktrace env)})

;; When activating a closure, we want the symbol table from the
;; environment overlaid with the closure's symtab. We also want the
;; closure's entire stack to appear as a single frame.
(defn- in-closure [env closure]
  (binds env (symtab closure)))

;; An "Appliable" is a value. It will be a macro or function
(defprotocol Apply
  (apply-to [this args env] "Apply arguments to a macro or function"))

(defn- arity-error? [receiver available]
  (not= (count receiver) (count available)))

;; Some environment juggling here to activate the environment from when
;; the lambda was created.
(defrecord Closure [arglist body closure]
  Apply
  (apply-to [this args env]
    (if (arity-error? arglist args)
      (break (raise (str "Expected " (count arglist) " arguments, got " (count args)) env))
      (let [env        (in-closure env closure)
            [args env] (eval-vec args env)]
        (if (errors? env)
          (break env)
          (let [env (binds env (zipmap arglist args))]
            (if (errors? env)
              (break env)
              (interpret body env))))))))

(defn mkclosure [arglist body env] (Closure. arglist body (closure env)))

;; When interpreted, a Lambda creates a Closure.
(defrecord Lambda [arglist body ast]
  Eval
  (frame [this]
    (mkframe 'Lambda arglist ast))

  (evaluate [this env]
    [(mkclosure arglist body env) env]))

(def mklambda ->Lambda)

;; A Primitive is a built-in function from Clojure. Applying it just
;; means calling the function.
(defrecord Primitive [f ast]
  Apply
  (apply-to [this args env]
    (let [[args env] (eval-vec args env)]
      (if (errors? env)
        (break env)
        [(apply f args) env]))))

(def mkprimitive ->Primitive)

(defrecord Map [kvs ast]
  Eval
  (frame [this]
    (mkframe 'Map kvs ast))

  (evaluate [this env]
    (let [[vs env] (eval-vec kvs env)]
      [(apply hash-map vs) env])))

(def mkmap ->Map)

(defrecord Statement [hd tl ast]
  Eval
  (frame [this] (mkframe hd tl ast))
  (evaluate [this env]
    (let [[fval env] (evaluate hd env)]
      (if (errors? env)
        (break env)
        (apply-to fval tl env)))))

(def mkstatement ->Statement)

(defrecord Let [bindings body ast]
  Eval
  (frame [this] (mkframe 'Let {:bindings bindings :body body} ast))
  (evaluate [this env]
    ;; todo - ensure even number of bindings
    (let [env (reduce
                (fn [env [k vexpr]]
                  (if (errors? env)
                    (break env)
                    (let [[v env] (interpret vexpr env)]
                      ;; todo - ensure lhs is a Symbol
                      (bind (:s k) v env))))
                env
                (partition 2 bindings))]
      (if (errors? env)
        (break env)
        (interpret body env)))))

(def mklet ->Let)
