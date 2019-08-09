(ns gos.world-test
  (:require [clojure.test :refer :all]
            [gos.ast :as ast]
            [gos.datomic-fixtures :as fix]
            [gos.db :as db]
            [gos.exec :as exec]
            [gos.parser :as parser]
            [gos.problems :refer [problems?]]))

(defn- process
  [state]
  (exec/process (dissoc state :tx-data)))

(defn- start-state []
  (exec/initial-state (fix/adapter)))

(def ^:private attr? fix/lookup-attribute)

(defn attr? [k exp]
  (is (= exp (select-keys (fix/lookup-attribute k) (keys exp)))))

(defn relation? [k]
  (let [e (db/rel (fix/adapter) k)]
    (is (some? e))
    (is (contains? e :relation/ordered-attributes))))

(defn qr [nm & pat]
  (->
    (ast/->Query [(ast/->QueryRelation nm pat)])
    (ast/evaluate  {:dbadapter (fix/adapter)})
    :query-result))

(defmacro after [strs & assertions]
  `(fix/with-database []
     (let [~'end-state (reduce #(exec/process (exec/with-input %1 %2)) (start-state) ~strs)]
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

  (testing "can be one attribute wide"
    (after ["attr name string one;"
            "relation system name;"]
      (is (not (problems? end-state)))
      (relation? :system)))

  (testing "can have a value added"
    (after ["attr name string one;"
            "attr age long one;"
            "relation person-age name age;"
            "person-age \"rajesh\" 25;"]
      (is (= #{["rajesh" 25]}
            (qr :person-age "rajesh" '?age)))))

  (testing "can have several values added"
    (after ["attr name string one;"
            "attr age long one;"
            "relation person-age name age;"
            "person-age \"douglas\" 25;"
            "person-age \"sarai\"   39;"
            "person-age \"rajesh\"  25;"]
      (is (= #{["rajesh" 25] ["douglas" 25]}
            (qr :person-age '?name 25)))))

  (testing "each value is unique"
    (after ["attr name string one;"
            "attr age long one;"
            "relation person-age name age;"
            "person-age \"rajesh\"  25;"
            "person-age \"rajesh\"  25;"
            "person-age \"rajesh\"  25;"]
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
              "person-age \"rajesh\" 25;"
              "employment-duration \"rajesh\" 3;"]
        (is (not (problems? end-state)))
        (is (= #{["rajesh" 25]}
              (qr :person-age "rajesh" '?age)))
        (is (= #{["rajesh" 3]}
              (qr :employment-duration "rajesh" '?age)))))))

(defn- pq [qstr]
  (->> qstr
    (exec/with-input (start-state))
    exec/process
    :value
    first))

(deftest queries
  (testing "a query looks like an instance with a logic variable"
    (after ["attr name string one;"
            "attr age long one;"
            "relation person-age name age;"
            "person-age \"rajesh\" 25;"]
      (is (= #{["rajesh" 25]} (:query-result (pq "person-age ?name 25;"))))))

  (testing "querying a single-value relation returns the set of values"
    (after ["attr name string one;"
            "relation person name;"
            "person \"douglas\";"
            "person \"sarai\";"
            "person \"rajesh\";"]
      (is (= #{["rajesh"] ["sarai"] ["douglas"]} (:query-result (pq "person ?n;"))))))

  (testing "a query can have multiple clauses"
    (after ["attr name string one;"
            "attr age long one;"
            "relation person-age name age;"
            "person-age \"rajesh\" 25;"
            "attr location string one;"
            "relation assignment name location;"
            "assignment \"rajesh\" \"southlake\";"]
      (is (= #{["rajesh" 25 "southlake"]} (:query-result (pq "person-age ?name 25, assignment ?name \"southlake\";"))))))

  (testing "junction"
    (after ["attr name string one;"
            "attr age long one;"
            "relation person-age name age;"
            "person-age \"douglas\" 25;"
            "person-age \"sarai\"   39;"
            "person-age \"rajesh\"  25;"]
      (is (=  #{["rajesh" 25]} (:query-result (pq "person-age ?name ?age, = ?name \"rajesh\";"))))))

  (testing "disjunction"
    (after ["attr name string one;"
            "attr age long one;"
            "relation person-age name age;"
            "person-age \"douglas\" 25;"
            "person-age \"sarai\"   39;"
            "person-age \"rajesh\"  25;"]
      (is (=  #{["douglas" 25]} (:query-result (pq "person-age ?name 25, != ?name \"rajesh\";"))))))

  (testing "inequality"
    (after ["attr name string one;"
            "attr age long one;"
            "relation person-age name age;"
            "person-age \"douglas\" 25;"
            "person-age \"sarai\"   39;"
            "person-age \"rajesh\"  25;"]
      (are [expected query] (= expected (:query-result (pq query)))
        #{["douglas" 25] ["rajesh" 25]} "person-age ?name ?age, < ?age 30;"
        #{["douglas" 25] ["rajesh" 25]} "person-age ?name ?age, <= ?age 25;"
        #{["sarai" 39]}                 "person-age ?name ?age, > ?age 30;"
        #{["sarai" 39]}                 "person-age ?name ?age, >= ?age 39;")))

  (testing "self-joins are allowed"
    (after ["attr a string one;"
            "attr b string one; "
            "relation foo a b;"
            "foo \"cake\" \"pie\";"
            "foo \"cake\" \"cake\";"]
      (is (= #{["cake"]} (:query-result (pq "foo ?a ?a;")))))))

(deftest query-fields
  (testing "fields are named for the logic variables"
    (after ["attr name string one;"
            "relation location name;"
            "location \"NYC\";"
            "location \"anywhere else\";"]
      (is (= '[?name] (:query-fields (pq "location ?name;"))))
      (is (= '[?cake] (:query-fields (pq "location ?cake;")))))

    (after ["attr name string one;"
            "relation seats name name name name;"
            "seats \"a\" \"b\" \"c\" \"d\";"]
      (is (= '[?a ?b ?c ?d] (:query-fields (pq "seats ?a ?b ?c ?d;")))))

    (after ["attr name string one;"
            "attr age long one;"
            "relation person-age name age;"
            "person-age \"douglas\" 25;"
            "person-age \"sarai\"   39;"
            "person-age \"rajesh\"  25;"
            "relation person-title name name;"
            "person-title \"douglas\" \"associate\";"
            "person-title \"sarai\"   \"staff\";"
            "person-title \"joan\"    \"host\";"]
      (is (= '[?name ?age ?title] (:query-fields (pq "person-age ?name ?age, person-title ?name ?title;")))))))

(deftest fields-are-ordered-not-named
  (testing "an instance's fields are kept separate"
    (after ["attr name string one;"
            "relation seats name name name name;"
            "seats \"a\" \"b\" \"c\" \"d\";"]
      (is (= #{["a" "b" "c" "d"]} (:query-result (pq "seats ?a ?b ?c ?d;")))))))

(deftest colon-is-shorthand-for-repetition
  (testing "in instances"
    (after ["attr id symbol one;"
            "relation diagram id;"
            "diagram: sysml-diagram behavior-diagram requirements-diagram;"]
      (is (=  #{['sysml-diagram] ['behavior-diagram] ['requirements-diagram]}
            (:query-result (pq "diagram ?a;")))))

    (after ["attr id symbol one;"
            "relation specialize id id;"
            "specialize sysml-diagram: behavior-diagram requirements-diagram structural-diagram;"]
      (is (=  #{'[sysml-diagram behavior-diagram]
                 '[sysml-diagram requirements-diagram]
                '[sysml-diagram structural-diagram]}
            (:query-result (pq "specialize ?par ?cld;")))))

    (after ["attr id symbol one;"
            "attr label string one;"
            "relation diagram-kind id label label;"
            "diagram-kind:
               activity-diagram         \"act\" \"Activity Diagram\"
               block-definition-diagram \"bdd\" \"Block Definition Diagram\"
               internal-block-diagram   \"ibd\" \"Internal Block Diagram\"
               package-diagram          \"pkg\" \"Package Diagram\";"]
      (is (= 4 (count (:query-result (pq "diagram-kind ?kind ?tag ?label;"))))))))

(deftest relations-can-constrain-values
  (testing "values in the confining relation are accepted"
    (after ["attr name string one;"
            "relation direction name;"
            "direction:\"N\" \"NE\" \"E\" \"SE\" \"S\" \"SW\" \"W\" \"NW\";"
            "relation arrow (name in direction);"]
      (pq "arrow \"N\";")))

  (testing "values not in the confining relation are rejected"
    (after ["attr name string one;"
            "relation direction name;"
            "direction:\"N\" \"NE\" \"E\" \"SE\" \"S\" \"SW\" \"W\" \"NW\";"
            "relation arrow (name in direction);"]
      (is (thrown? AssertionError (pq "arrow \"to the knee\";"))))))

#_(deftest let-expressions
  (testing "return maps in their results"
    (let [result (exec/process (exec/with-input (start-state)
                                  "{ };"))]
      (is (ok? result))
      (is (= {} (-> result :response :body))))
    )

  (testing "allow constant values"
    (let [result (exec/process (exec/with-input (start-state)
                                  "{ a => b };"))]
      (is (ok? result))
      (is (= {'a 'b} (-> result :response :body)))))

  (testing "allow queries as the right hand side"
    (after ["attr name string one;"
            "relation direction name;"
            "direction:\"N\" \"NE\" \"E\" \"SE\" \"S\" \"SW\" \"W\" \"NW\";"]

      (let [result (exec/process (exec/with-input (start-state)
                                    "{ dir => direction ?d; };"))]
        (is (ok? result))
        (is (= {'dir #{["N"] ["NE"] ["E"] ["SE"] ["S"] ["SW"] ["W"] "NW"}})))))

  (testing "allow multiple bindings"
    (after ["attr name string one;"
            "relation person name;"
            "person: \"rajesh\" \"sarai\" \"douglas\";"
            "relation direction name;"
            "direction:\"N\" \"NE\" \"E\" \"SE\" \"S\" \"SW\" \"W\" \"NW\";"]

      (let [result (exec/process (exec/with-input (start-state)
                                    "{ dir => direction ?d;
                                       person => person ?name; };"))]
        (is (ok? result))
        (is (= {'dir #{["N"] ["NE"] ["E"] ["SE"] ["S"] ["SW"] ["W"] ["NW"]}
                'person #{["rajesh"] ["sarai"] ["douglas"]}})))))

  (testing "allow multiple clauses on the right hand side"
    (after ["attr name string one;"
            "attr age long one;"
            "relation person-age name age;"
            "person-age \"douglas\" 25;"
            "person-age \"sarai\"   39;"
            "person-age \"rajesh\"  25;"
            "relation direction name;"
            "direction:\"N\" \"NE\" \"E\" \"SE\" \"S\" \"SW\" \"W\" \"NW\";"]

      (let [result (exec/process (exec/with-input (start-state)
                                    "{ dir => direction ?d;
                                       person => person-age ?name ?age,
                                                 = ?age 39; };"))]
        (is (ok? result))
        (is (= {'dir #{["N"] ["NE"] ["E"] ["SE"] ["S"] ["SW"] ["W"] ["NW"]}
                'person #{["sarai"]}}))))))
