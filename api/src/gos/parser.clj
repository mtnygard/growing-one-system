(ns gos.parser
  (:require [clojure.edn :as edn]
            [gos.date :as date]
            [gos.problems :refer [with-problems]]
            [gos.seq :refer [sequential-tree]]
            [instaparse.core :as insta]
            [clojure.string :as str]
            [gos.ast :as ast]))

;; ========================================
;; Grammar

(def ^:private whitespace
  (insta/parser
   "whitespace = #'\\s+'"))

(def ^:private whitespace-or-comments
  (insta/parser
   "ws-or-comments = #'\\s+' | comments
    comments = comment+
    comment = '//' inside-comment* '\n'
    inside-comment =  !( '\n' | '//' ) #'.' | comment"
   :auto-whitespace whitespace))

(def ^:private grammar
  (insta/parser
   "input            = expr*
    <expr>           = ( attribute-decl / relation-decl / map-expr / instance-expr / query-expr ) <';'>

    attribute-decl   = <'attr'> name type cardinality

    relation-decl    = 'relation' name attrref+
    <attrref>        = name | constrained-name
    constrained-name = <'('> name constraint <')'>
    constraint       = 'in' name

    map-expr         = <'{'> ( name <'=>'> expr )* <'}'>

    instance-expr    = name repeat? value ( repeat? value )*
    value            = symbol | string-literal | long-literal | boolean-literal | date-literal

    query-expr       = query-clause ( <','> query-clause )*
    <query-clause>   = instance-clause | operator-clause
    instance-clause  = name value*
    operator-clause  = operator value*

    repeat           = <':'>
    name             = #\"[a-zA-Z_][a-zA-Z0-9_\\-\\?]*\"
    type             = #\"[a-zA-Z_][a-zA-Z0-9]*\"
    symbol           = #\"[a-zA_Z_0-9\\?][a-zA_Z_0-9\\?\\-\\$%*]*\"
    string-literal   = #\"\\\"(\\.|[^\\\"])*\\\"\"
    long-literal     = #\"-?[0-9]+\"
    date-literal     = #\"[0-9]{4}-[0-9]{2}-[0-9]{2}\"
    boolean-literal  = 'true' | 'false'
    cardinality      = 'one' | 'many'
    operator         = '=' | '!=' | '<' | '<=' | '>' | '>='"
   :auto-whitespace whitespace-or-comments))

;; ========================================
;; Building the AST

(defn- lvar? [x]
  (and (symbol? x) (str/starts-with? (name x) "?")))

(defn- has-lvars?        [x] (some lvar? (sequential-tree x)))

(defn- instance-or-query [vs]
  (if (has-lvars? vs)
    (ast/->Query [(ast/->QueryRelation (first vs) (rest vs))])
    (ast/->Instance (first vs) (rest vs))))

(defn- transform [parse-tree]
  (insta/transform
    {:input            (fn [& exprs] (ast/->Exprs exprs))
     :attribute-decl   ast/->Attribute
     :relation-decl    (fn [_ r & xs] (ast/->Relation r xs))
     :instance-expr    (fn [& xs] (instance-or-query xs))
     :instance-clause  (fn [r & xs] (ast/->QueryRelation r xs))
     :operator-clause  (fn [& pat]  (ast/->QueryOperator pat))
     :query-expr       (fn [& clauses] (ast/->Query clauses))
     :map-expr         (fn [& bindings] (ast/->Binding (apply hash-map bindings)))
     :name             keyword
     :type             keyword
     :cardinality      keyword
     :value            identity
     :symbol           symbol
     :constraint       (fn [x & more] (list* (keyword x) more))
     :constrained-name vector
     :string-literal   edn/read-string
     :long-literal     edn/read-string
     :boolean-literal  edn/read-string
     :date-literal     date/yyyy-mm-dd
     :binding          (fn [& xs] (apply hash-map xs))
     :repeat           (constantly :repeat)
     :operator         symbol}
   parse-tree))

;; ========================================
;; Public interface

(defn parse [body]
  (let [result (insta/parse grammar body)]
    (if (insta/failure? result)
      (throw (ex-info "Parse error" {:failure result}))
      (transform result))))
