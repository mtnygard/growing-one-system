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

(deftest testing-lambdas
  (testing "lambda at top level"
    (let [env empty-environment
          r   (->> env
                (bind 'foo (mklambda '[x] (mksym 'x {}) env))
                (interpret (mkstatement 'foo [(mklit 15 {})] {})))]
      (is (= 15 (result r)))
      (is (not (errors? (final-env r))))))

  (testing "error inside lambda body"
    (let [env empty-environment
          r   (->> env
                (bind 'foo (mklambda '[x] (mksym 'x {}) env))
                (interpret (mkstatement 'foo [(mksym 'blarg {})] {})))]
      (is (nil? (result r)))
      (is (errors? (final-env r))))))

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

;; todo - lambda should capture its environment and return a closure.
;; The Lambda is evaluated to produce a closure. The closure is
;; applied to zero or more arguments.

#_(run-tests)
