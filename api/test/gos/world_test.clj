(ns gos.world-test
  (:require [clojure.test :refer :all]
            [gos.world :as world]
            [gos.problems :refer [problems?]]
            [datomic.api :as d]
            [gos.datomic-fixtures :as fix]))

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

(defn- process [body]
  (let [conn           fix/*current-db-connection*
        starting-state {:conn  conn
                        :db    (d/db conn)
                        :world {}}]
    (world/process starting-state body)))

(def ^:private attr? fix/lookup-attribute)

(defn attr? [k exp]
  (= exp (select-keys (fix/lookup-attribute k) (keys exp))))

(deftest attribute
  (testing "can be added"
    (fix/with-database []
      (let [end-state (process "attr name string one;")]
        (println end-state)
        (is (not (problems? end-state)))
        (is (attr? :name {:db/ident :name}))))))
