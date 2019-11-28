(ns gos.interp
  (:refer-clojure :exclude [find map?]))

(defmacro def- [nm & decls]
  (list* `def (with-meta nm (assoc (meta nm) :private true)) decls))

;; Interpret commands in the style of a Lisp. Each command is a list
;; of expressions. The first one is either a "function" or a "macro".
;; The difference is whether the forms are evaluated prior to invoking
;; the code bound to the name of the first form.

;; The "environment" hold the state of a virtual machine.
(def- empty-environment {:symtab {} :stack [] :error-stack []})

;; Names are bound in an environment
(def-  symtab :symtab)
(defn- bind [s v env]  (assoc-in env [symtab s] v))
(defn- find [s env]    (get-in env [symtab s]))
(defn- binds [env svs] (reduce-kv (fn [env s v] (bind s v env)) env svs))

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
(def- stacktrace :stack)

;; "Raising" an error means we stop evaluating
(defn- push-error [err env] (update-in env [:error-stack] conj err))
(defn- clear-error [env]    (assoc-in env [:error-stack] []))
(defn- raise [err env]      (push-error {:cause err :at (stacktrace env)} env))
(def-  errors               :error-stack)
(defn- errors? [env]        (not-empty (errors env)))

;; A closure is a frozen form of the current namespace, with the stack
;; recorded for use in debugging
(defn- mkclosure [env]
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

;; A Primitive is a built-in function from Clojure. Applying it just
;; means calling the function.
(defrecord Primitive [f]
  Apply
  (apply-to [this args env]
    (let [[args env] (eval-vec args env)]
      (if (errors? env)
        (break env)
        [(apply f args) env]))))

(defn- arity-error? [receiver available]
  (not= (count receiver) (count available)))

(declare interpret)

;; Some environment juggling here to activate the closure from when
;; the lambda was created.
(defrecord Lambda [arglist body closure]
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

(defn lambda [arglist body closure] (Lambda. arglist body closure))

(comment




  )

;; Evaluating a form is polymorphic
(defprotocol Eval
  (frame [this]        "Return a stack frame describing this form")
  (evaluate [this env] "Evaluation depends on what the first expression is. Return pair of value and new env"))

;; "break" means we are raising an error up the stack.
(defn- break  [env] [nil env])

;; Interpretation begins with a top-level form and an environment.
;; The environment probably contains bindings from previous forms.
(defn- interpret [top env]
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

(defn sym [s ast] (Symbol. s ast))

(defrecord Literal [val ast]
  Eval
  (frame [this]
    (mkframe 'Literal nil ast))

  (evaluate [this env]
    [val env]))

(defn lit [v ast] (Literal. v ast))

(defn- eval-vec [xs env]
  (reduce
    (fn [[vals env] f]
      (let [[v e] (interpret f env)]
        [(conj vals v) e]))
    [[] env]
    xs))

(defrecord Map [kvs ast]
  Eval
  (frame [this]
    (mkframe 'Map kvs ast))

  (evaluate [this env]
    (let [[vs env] (eval-vec kvs env)]
      [(apply hash-map vs) env])))

(defn mapf [kvs ast] (Map. kvs ast))

(defrecord Statement [hd tl ast]
  Eval
  (frame [this] (mkframe hd tl ast))
  (evaluate [this env]
    (let [fval (find hd env)]
      (if-not fval
        (break (raise (str "Found " hd " in function position, but it is not callable.") env))
        (apply-to fval tl env)))))

(defn statement [hd tl ast] (Statement. hd tl ast))

(comment

  (interpret (mapf [(sym 'ofo {}) (sym 'bar {})] {})
    empty-environment)

  (->> empty-environment
    (bind 'foo :a))

  (->> empty-environment
    (bind 'foo :a)
    (interpret (sym 'foo {})))


  (->> empty-environment
    (bind 'foo :b)
    (interpret
      (mapf [(sym 'foo {}) (lit 10 {})
             (lit :a {})  (lit "this is the day" {})]
        {})))

  (->> empty-environment
    (bind 'print (Primitive. println))
    (bind 'foo :b)
    (interpret
      (statement 'print
        [(mapf [(sym 'foo {}) (lit 10 {})
                (lit :a {})  (lit "this is the day" {})]
           {})]
        {})))

  (->> empty-environment
    (bind 'print (Primitive. println))
    (interpret
      (statement 'print
        [(mapf [(sym 'foo {}) (lit 10 {})
                (lit :a {})  (lit "this is the day" {})]
           {})]
        {})))



  )

;; Tests. These will move into their own namespace once this stabilizes

(def result first)
(def final-env second)
(defmacro progn [& body]
  `(->> empty-environment ~@body))

(require '[clojure.test :refer :all])

(deftest do-nothing-sensibly
  (testing "return a literal"
    (is (= :a (result
                (progn
                  (bind 'foo :a)
                  (interpret (sym 'foo {})))))))

  (testing "return a map"
    (is (= {:a "this is the day" :b 10}
          (result
            (progn
              (bind 'foo :b)
              (interpret (mapf [(sym 'foo {}) (lit 10 {})
                                (lit :a {})  (lit "this is the day" {})] {}))))))))

(deftest testing-lambdas
  (testing "lambda at top level"
    (let [env empty-environment
          r   (->> env
                (bind 'foo (lambda '[x] (sym 'x {}) (mkclosure env)))
                (interpret (statement 'foo [(lit 15 {})] {})))]
      (is (= 15 (result r)))
      (is (not (errors? (final-env r))))))

  (let [env empty-environment
        r   (->> env
              (bind 'foo (lambda '[x] (sym 'x {}) (mkclosure env)))
              (interpret (statement 'foo [(sym 'blarg {})] {})))]
    (is (nil? (result r)))
    (is (errors? (final-env r)))))


(run-tests)
