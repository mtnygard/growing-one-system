(ns gos.parse-test
  (:require [clojure.test :refer :all]
            [datomic.api :as d]
            [fern :as f]
            [gos.api :as api]
            [gos.ast :as ast]
            [gos.datomic-fixtures :as fix]
            [gos.db :as db]
            [gos.parser :as parser])
  (:import clojure.lang.ExceptionInfo))

(defn- p [body]
  (parser/parse body))

(def first-expr (comp first :exprs))
(def first-clause (comp first :clauses first-expr))

(deftest parse-inputs
  (testing "Empty input is accepted"
    (is (p "")))

  (testing "Comments are ignored"
    (is (p "// this is a comment\n")))

  (testing "parse error handling"
    (is (thrown? ExceptionInfo (p "no such phrase"))))

  (testing "string literal"
    (is (p "x \"this is a string\";")))

  (testing "several declaration forms"
    (are [kind? s] (kind? (first-expr (p s)))
      ast/attribute? "attr name string one;"
      ast/relation?  "relation name repo;"
      ast/instance?  "code \"growing-one-system\" \"https://github.com/mtnygard/growing-one-system\";"
      ast/query?     "person ?n;"
      ast/binding?   "{ person => person ?name; };"
      ))

  (testing "query clauses are also nodes"
    (are [kind? s] (kind? (first-clause (p s)))
      ast/query-relation? "person-age ?name 25, person ?name;"
      ast/query-relation? "person-age ?name 25;"
      ast/query-operator? "= 1 2;"))

  (testing "Multiple statements"
    (p "attr name string one; attr repo url many; relation code-location name repo;")))
