(ns gos.world-test
  (:require [clojure.test :refer :all]
            [gos.world :as world]
            [gos.problems :refer [problems?]]
            [datomic.api :as d]
            [gos.datomic-fixtures :as fix]
            [gos.db :as db]
            [fern :as f]))

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
    (is (= :instance (ffirst (:parsed (p "code growing-one-system-book https://github.com/mtnygard/growing-one-system-book;")))))))

(defn- process
  [start-state body]
  (world/process (dissoc start-state :tx-data) body))

(defn- start-state []
  (world/current-state (fix/adapter) {}))

(def ^:private attr? fix/lookup-attribute)

(defn attr? [k exp]
  (is (= exp (select-keys (fix/lookup-attribute k) (keys exp)))))

(defn relation? [k]
  (let [e (db/e (fix/adapter) k)]
    (is (some? e))
    (is (contains? e :relation/ordered-attributes))))

(defn qr [nm & pat]
  (apply world/query-relation (fix/adapter) nm pat))

(defmacro after [strs & assertions]
  `(fix/with-database []
     (let [~'end-state (reduce process (start-state) ~strs)]
       ~@assertions)))

(deftest attribute
  (testing "can be added"
    (testing "as single-valued"
      (after ["attr name string one;"]
        (is (not (problems? end-state)))
        (attr? :name {:db/ident       :name
                      :db/valueType   :db.type/string
                      :db/cardinality :db.cardinality/one})))

    (testing "can be multi-valued"
      (after ["attr scores long many;"]
        (is (not (problems? end-state)))
        (attr? :scores {:db/ident       :scores
                        :db/valueType   :db.type/long
                        :db/cardinality :db.cardinality/many}))))

  (testing "can be expanded"
    (after ["attr name string one;"
            "attr name string many;"]
      (is (not (problems? end-state)))
      (attr? :name {:db/ident       :name
                    :db/cardinality :db.cardinality/many}))))

(deftest relation
  (testing "can be added"
    (after ["attr name string one;"
            "attr age long one;"
            "relation person-age name age;"]
      (is (not (problems? end-state)))
      (relation? :person-age)))

  (testing "can have a value added"
    (after ["attr name string one;"
            "attr age long one;"
            "relation person-age name age;"
            "person-age rajesh  25;"]
      (is (= #{["rajesh" 25]}
            (qr :person-age "rajesh" '_)))))

  (testing "can have several values added"
    (after ["attr name string one;"
            "attr age long one;"
            "relation person-age name age;"
            "person-age douglas 25;"
            "person-age sarai   39;"
            "person-age rajesh  25;"]
      (is (= #{["rajesh" 25] ["douglas" 25]}
            (qr :person-age '_ 25)))))

  (testing "each value is unique"
    (after ["attr name string one;"
            "attr age long one;"
            "relation person-age name age;"
            "person-age rajesh  25;"
            "person-age rajesh  25;"
            "person-age rajesh  25;"]
      (is (= 1
            (count (qr :person-age "rajesh" 25))))))

  (testing "different relations with the same attributes"
    (testing "are allowed"
      (after ["attr name string one;"
              "attr years long one;"
              "relation person-age name years;"
              "relation employment-duration name years;"]
        (is (not (problems? end-state)))
        (relation? :person-age)
        (relation? :employment-duration)))

    (testing "and their values are distinct"
      (after ["attr name string one;"
              "attr years long one;"
              "relation person-age name years;"
              "relation employment-duration name years;"
              "person-age rajesh 25;"
              "employment-duration rajesh 3;"]
        (is (not (problems? end-state)))
        (is (= #{["rajesh" 25]}
              (qr :person-age "rajesh" '_)))
        (is (= #{["rajesh" 3]}
              (qr :employment-duration "rajesh" '_))))))

  (testing "a helper function will query a specific relation"
    (after ["attr name string one;"
            "attr age long one;"
            "relation person-age name age;"
            "person-age rajesh  25;"]
      (let [qfn (query-helper-fn :person-age)]
        (is (= 1 (count (qfn (fix/adapter) "rajesh" '_)))))))

  )
