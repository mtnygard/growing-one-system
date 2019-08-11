(ns gos.ast
  (:refer-clojure :exclude [instance?])
  (:require [clojure.string :as str]
            [gos.db :as db]
            [gos.debug :as debug]))

;; Abstract Syntax Tree

(defprotocol AST
  (problems [this state] "Return coll of problems with this AST node")
  (evaluate [this state] "Return the AST node's value."))

;; Sanity checks
(defn assert-has-attributes [nm attrs]
  (assert
    (not (empty? attrs))
    (str "Not a relation: " nm)))

(defn assert-sufficient-values [nm attrs vals]
  (assert (= (count attrs) (count vals))
    (str
      "Relation " nm " has " (count attrs)
      " attributes but " (count vals) " values were supplied.")))

(defn assert-sufficient-pattern [nm attrs pattern]
  (assert (= (count attrs) (count pattern))
    (str
      "Relation " nm " has " (count attrs)
      " attributes but the query pattern has " (count pattern) " markers.")))

(defn assert-attribute-exists [attrnm actualtype]
  (assert (some? actualtype)
    (str "Attribute " attrnm " does not exist.")))

(defn relation [dbadapter nm] (db/rel dbadapter nm))
(def  relation-attributes :relation/ordered-attributes)
(defn relation? [e]      (contains? e relation-attributes))

;; Querying
(defn- lvar? [x]
  (and (symbol? x) (str/starts-with? (name x) "?")))

(defn- genlvar
  ([] (gensym "?"))
  ([prefix] (gensym (str "?" prefix))))

(defn- k->lv [kw]
  (genlvar (name kw)))

(defn- lparms [attrs pat]
  (mapv #(if (lvar? %2) %2 (k->lv %1)) attrs pat))

(defn- lclause [esym attrs lv]
  (mapv vector (repeat esym) attrs lv))

(defn- mask [pred rvals maskvals]
  (map #(if-not (pred %1 %2) %1) rvals maskvals))

(defn- in-clause [findvars pattern]
  (keep identity (mask #(lvar? %2) findvars pattern)))

(defn- run-query
  [dbadapter q]
  (db/q dbadapter (dissoc q :args) (:args q)))

(def empty-query
  {:find  []
   :in    ['$]
   :where []
   :args  []})

(defn- merge-query
  [q x]
  (-> q
    (update :find  concat (remove (set (:find q)) (:find x)))
    (update :in    concat (:in x))
    (update :where concat (:where x))
    (update :args  concat (:args x))))

;; ========================================
;; AST Nodes

(defrecord Exprs [exprs]
  AST
  (problems [this state]
    (mapv #(problems % state) exprs))

  (evaluate [this state]
    (when debug/*debug-evaluation*
      (debug/indent-print state "Exprs"))
    (mapv #(evaluate % (debug/indent state)) exprs)))

(defrecord Attribute [nm type card]
  AST
  (problems [this state]
    ;; todo - check that the type exists, nm is a keyword
    nil)

  (evaluate [this state]
    (when debug/*debug-evaluation*
      (debug/indent-print state "Attribute"))
    @(db/transact (:dbadapter state)
       (db/mkattr (:dbadapter state) nm type card))))

(defrecord Relation [nm attrs]
  AST
  (problems [this state]
    ;; todo - check attrs exist
    ;; todo - check any constraints are legit
    nil)

  (evaluate [this state]
    (when debug/*debug-evaluation*
      (debug/indent-print state "Relation"))
    (mapv #(update
             (deref (db/transact (:dbadapter state) %))
             :tx-data
             seq)
      (db/mkrel (:dbadapter state) nm attrs))))

;; if :repeat appears in the vals vector, hold the stuff to the left
;; constant, and chunk the stuff on the right as needed to fill the #
;; attrs
(defn- left-of [v xs]
  (take-while #(not= v %) xs))

(defn- right-of [v xs]
  (next (drop-while #(not= v %) xs)))

(defn unpack-repeats
  [width vals]
  (let [constant-part  (left-of :repeat vals)
        foreach-part   (right-of :repeat vals)
        missing-values (- width (count constant-part))
        leftover       (if (= 0 missing-values) 0 (rem (count foreach-part) missing-values))]
    (if (empty? foreach-part)
      [constant-part]
      (do
        (assert (= 0 leftover) (str "Not enough values, need a multiple of " missing-values ". Had " leftover " extra values."))
        (map concat
          (repeat constant-part)
          (partition-all missing-values (right-of :repeat vals)))))))

(defrecord Instance [relnm vals]
  AST
  (problems [this state]
    ;; todo - check rel exists
    ;; todo - check enough vals
    nil)

  (evaluate [this state]
    (when debug/*debug-evaluation*
      (debug/indent-print state "Instance"))
    (let [rel (relation (:dbadapter state) relnm)
          attrs (relation-attributes rel)]
      (assert-has-attributes relnm attrs)
      (let [vals (unpack-repeats (count attrs) vals)]
        (mapv
          #(update
             (deref (db/transact (:dbadapter state) [(db/mkent (:dbadapter state) relnm attrs %)]))
             :tx-data
             seq)
          vals)))))

(defrecord Query [clauses]
  AST
  (problems [this state]
    nil)

  (evaluate [this state]
    (when debug/*debug-evaluation*
      (debug/indent-print state "Query"))
    (let [dbadapter (:dbadapter state)
          query     (reduce merge-query empty-query (map #(evaluate % state) clauses))]
      {:query-result (run-query dbadapter query)
       :query-fields (:find query)})))

(defrecord QueryRelation [relnm pattern]
  AST
  (problems [this state]
    ;; todo - check relation exists
    ;; (assert-has-attributes nm attrs)
    ;; (assert-sufficient-pattern nm attrs pattern)
    nil)

  (evaluate [this state]
    (when debug/*debug-evaluation*
      (debug/indent-print state "QueryRelation"))
    (let [dbadapter (:dbadapter state)
          rel       (relation dbadapter relnm)
          attrs     (relation-attributes rel)
          ident     (:db/ident rel)
          lv        (lparms attrs pattern)
          esym      (gensym "?e")
          where     (lclause esym attrs lv)]
      {:find  (into [] (distinct lv))
       :in    (in-clause lv pattern)
       :where (into [[esym :entity/relation ident]] where)
       :args  (remove lvar? pattern)})))

(defrecord QueryOperator [pattern]
  AST
  (problems [this state]
    nil)

  (evaluate [this state]
    (when debug/*debug-evaluation*
      (debug/indent-print state "QueryOperator"))
    {:where [[(list* pattern)]]}))

(defrecord Binding [bindings]
  AST
  (problems [this state]
    ;; todo - analyze RHS of bindings for problems.
    nil
    )

  (evaluate [this state]
    (when debug/*debug-evaluation*
      (debug/indent-print state "Bindings"))
    (zipmap
      (keys bindings)
      (mapv #(evaluate % (debug/indent state)) (vals bindings)))))

;; Testing membership

(def ast-node? (partial satisfies? AST))

(defn- i? [cls] (partial clojure.core/instance? cls))

(def attribute?      (i? Attribute))
(def relation?       (i? Relation))
(def instance?       (i? Instance))
(def query?          (i? Query))
(def query-relation? (i? QueryRelation))
(def query-operator? (i? QueryOperator))
(def binding?        (i? Binding))
