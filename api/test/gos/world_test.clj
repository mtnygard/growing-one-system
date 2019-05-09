(ns gos.world-test
  (:require [clojure.test :refer :all]
            [gos.world :as world]
            [gos.problems :refer [problems?]]))

(defn- p [body]
  (world/parse {} body))

(deftest parse-inputs
  (testing "Empty input is accepted"
    (is (not (problems? (p "")))))
  
  (testing "Comments are ignored"
    (is (not (problems? (p "// this is a comment\n")))))
  
  (testing "parse error handling"
    (is (problems? (p "no such phrase"))))

  (testing "Defining an attribute"
    (is (not (problems? (p "attr name string one;"))))
    (is (= :attribute (ffirst (:parsed (p "attr aliases string many;"))))))

  (testing "Defining a relation"
    (is (not (problems? (p "relation name repo;"))))
    (is (= :relation (ffirst (:parsed (p "relation name repo;"))))))

  (testing "Multiple statements"
    (is (not (problems? (p "attr name string one; attr repo url many; relation code-location name repo;")))))
  
  (testing "Making an element of a relation"
    (is (not (problems? (p "code growing-one-system https://github.com/mtnygard/growing-one-system;"))))
    (is (= :instance (ffirst (:parsed (p "code growing-one-system-book https://github.com/mtnygard/growing-one-system-book;"))))))
  )