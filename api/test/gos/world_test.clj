(ns gos.world-test
  (:require [clojure.test :refer :all]
            [gos.world :as world]
            [gos.problems :refer [problems?]]
            [datomic.api :as d]
            [gos.datomic-fixtures :as fix]
            [gos.db :as db]))

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

(defn- process
  [start-state body]
  (world/process start-state body))

(defn- start-state []
  (world/current-state (fix/adapter) {}))

(def ^:private attr? fix/lookup-attribute)

(defn attr? [k exp]
  (= exp (select-keys (fix/lookup-attribute k) (keys exp))))

(defmacro after [strs & assertions]
  `(fix/with-database []
     (let [~'end-state (reduce process (start-state) ~strs)]
       ~@assertions)))

(deftest attribute
  (testing "can be added"
    (testing "as single-valued"
      (after ["attr name string one;"]
        (is (not (problems? end-state)))
        (is (attr? :name {:db/ident       :name
                          :db/valueType   :db.type/string
                          :db/cardinality :db.cardinality/one}))))
    (testing "or multi-valued"
      (after ["attr scores long many;"]
        (is (not (problems? end-state)))
        (is (attr? :scores {:db/ident       :scores
                            :db/valueType   :db.type/long
                            :db/cardinality :db.cardinality/many})))))

  (testing "can be expanded"
    (after ["attr name string one;" "attr name string many;"]
        (is (not (problems? end-state)))
        (is (attr? :scores {:db/ident       :name
                            :db/cardinality :db.cardinality/many}))))





  )
