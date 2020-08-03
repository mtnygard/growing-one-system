(ns gos.interp-test
  (:refer-clojure :exclude [find])
  (:require [gos.interp :refer :all]
            [clojure.test :refer :all]))

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
                  (interpret (mksym 'foo {})))))))

  (testing "return a map"
    (is (= {:a "this is the day" :b 10}
          (result
            (progn
              (bind 'foo :b)
              (interpret (mkmap [(mksym 'foo {}) (mklit 10 {})
                                (mklit :a {})  (mklit "this is the day" {})] {}))))))))



(deftest testing-bindings
  (testing "Empty bindings"
    (let [r (progn
              (interpret
                (mklet []
                  (mklit 15 {})
                  {})))]
      (is (= 15 (result r)))
      (is (not (errors? (final-env r))))))

  (testing "One binding"
    (let [r (progn
              (interpret
                (mklet [(mksym 'x {}) (mklit 15 {})]
                  (mksym 'x {})
                  {})))]
      (is (= 15 (result r)))
      (is (not (errors? (final-env r))))))

  (testing "Two bindings"
    (let [r (progn
              (interpret
                (mklet
                  [(mksym 'x {}) (mklit "cake" {})
                   (mksym 'A {}) (mklit true {})]
                  (mksym 'A {})
                  {})))]
      (is (result r))
      (is (not (errors? (final-env r))))))  )

(deftest testing-lambdas
  (testing "lambda at top level"
    (let [r (progn
              (interpret
                (mklet [(mksym 'foo {}) (mklambda '[x] (mksym 'x {}) {})]
                  (mkstatement (mksym 'foo {}) [(mklit 15 {})] {})
                  {})))]
      (is (= 15 (result r)))
      (is (not (errors? (final-env r))))))

  (testing "error inside lambda body"
    (let [r (progn (interpret
                     (mklet [(mksym 'foo {}) (mklambda '[x] (mksym 'x {}) {})]
                       (mkstatement (mksym 'foo {}) [(mksym 'blarg {})] {})
                       {})))]
      (is (nil? (result r)))
      (is (errors? (final-env r)))))

  (testing "lambdas capture their environment when executed"
    (let [r (progn
              (interpret
                (mklet [(mksym 'x {}) (mklit 15 {})
                        (mksym 'foo {}) (mklambda [] (mksym 'x {}) {})]
                  (mkstatement (mksym 'foo {}) [] {})
                  {})))]
      (is (= 15 (result r)))
      (is (not (errors? (final-env r)))))

    (let [r (progn
              (interpret
                (mklet [(mksym 'x {}) (mklit 15 {})
                        (mksym 'foo {}) (mklambda [] (mksym 'x {}) {})
                        (mksym 'x {}) (mklit false {})]
                  (mkstatement (mksym 'foo {}) [] {})
                  {})))]
      (is (= 15 (result r)))
      (is (not (errors? (final-env r))))))

  (testing "theorems from lambda calculus"
    (let [r (progn
              (interpret
                (mklet [(mksym 't {}) (mklambda '[x y] (mksym 'x {}) {})]
                  (mkstatement (mksym 't {}) [(mklit 9 {}) (mklit :acorn {})] {})
                  {})))]
      (is (= 9 (result r))))

    (let [r (progn
              (interpret
                (mkstatement
                  (mklambda '[x y] (mksym 'x {}) {})
                  [(mklit 15 {}) (mklit :hazelnut {})]
                  {})))]
      (is (not (errors? (final-env r))))
      (is (= 15 (result r))))))

(deftest special-forms
  (testing "receive their arguments unevaluated"
    (let [r (progn
              (interpret
                (mklet [(mksym 'm {})
                        (mkspecial (mksym 'if {}) [(mksym 'form {})] (mkstatement (mksym 'count {}) (mksym 'form {})))]
                  {}
                  )))]
      (is (not (errors? (final-env r))))
      )
    )
  )

(run-tests)
