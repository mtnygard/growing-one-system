(ns gos.world-test
  (:require [clojure.test :refer :all]
            [gos.world :as world]
            [gos.problems :refer [problems?]]
            [datomic.api :as d]
            [gos.datomic-fixtures :as fix]
            [gos.db :as db]
            [fern :as f]))

(defn- p [body]
  (world/parse {:body body}))

(deftest parse-inputs
  (testing "Empty input is accepted"
    (is (not (problems? (p "")))))

  (testing "Comments are ignored"
    (is (not (problems? (p "// this is a comment\n")))))

  (testing "parse error handling"
    (is (problems? (p "no such phrase"))))

  (testing "string literal"
    (is (not (problems? (p "x \"this is a string\";")))))

  (testing "Defining an attribute"
    (is (not (problems? (p "attr name string one;"))))
    (is (= :attribute (ffirst (:parsed (p "attr aliases string many;"))))))

  (testing "Defining a relation"
    (is (not (problems? (p "relation name repo;"))))
    (is (= :relation (ffirst (:parsed (p "relation name repo;"))))))

  (testing "Multiple statements"
    (is (not (problems? (p "attr name string one; attr repo url many; relation code-location name repo;")))))

  (testing "Making an element of a relation"
    (is (not (problems? (p "code \"growing-one-system\" \"https://github.com/mtnygard/growing-one-system\";"))))
    (is (= :instances (ffirst (:parsed (p "code \"growing-one-system-book\" \"https://github.com/mtnygard/growing-one-system-book\";"))))))

  (testing "Queries have logic variables"
    (is (not (problems? (p "person ?n;"))))
    (is (= :query (ffirst (:parsed (p "person ?n;")))))))

(defn- process
  [state]
  (world/process (dissoc state :tx-data)))

(defn- start-state []
  (world/initial-state (fix/adapter)))

(def ^:private attr? fix/lookup-attribute)

(defn attr? [k exp]
  (is (= exp (select-keys (fix/lookup-attribute k) (keys exp)))))

(defn relation? [k]
  (let [e (db/rel (fix/adapter) k)]
    (is (some? e))
    (is (contains? e :relation/ordered-attributes))))

(defn qr [nm & pat]
  ((world/query-helper-fn (fix/adapter) nm) pat))

(defmacro after [strs & assertions]
  `(fix/with-database []
     (let [~'end-state (reduce #(process (world/with-input %1 %2)) (start-state) ~strs)]
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

  (testing "can have several values added at once"
    (after ["attr name string one;"
            "attr age long one;"
            "relation person-age name age;"
            "person-age \"douglas\" 25,
             person-age \"sarai\"   39,
             person-age \"rajesh\"  25;"]
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
              (qr :employment-duration "rajesh" '?age))))))

  (testing "a helper function will query a specific relation"
    (after ["attr name string one;"
            "attr age long one;"
            "relation person-age name age;"
            "person-age \"rajesh\" 25;"]
      (let [qfn (world/query-helper-fn (fix/adapter) :person-age)]
        (is (= 1 (count (qfn ["rajesh" '?age]))))))))

(defn- ok? [{:keys [response]}]
  (= 200 (:status response)))

(deftest queries
  (testing "a query looks like an instance with a logic variable"
    (after ["attr name string one;"
            "attr age long one;"
            "relation person-age name age;"
            "person-age \"rajesh\" 25;"]
      (let [result (world/process (world/with-input (start-state) "person-age ?name 25;"))]
        (is (ok? result))
        (is (= #{["rajesh" 25]} (-> result :response :body :query-result))))))

  (testing "querying a single-value relation returns the set of values"
    (after ["attr name string one;"
            "relation person name;"
            "person \"douglas\";"
            "person \"sarai\";"
            "person \"rajesh\";"]
      (let [result (world/process (world/with-input (start-state) "person ?n;"))]
        (is (ok? result))
        (is (= #{["rajesh"] ["sarai"] ["douglas"]} (-> result :response :body :query-result))))))

  (testing "a query can have multiple clauses"
    (after ["attr name string one;"
            "attr age long one;"
            "relation person-age name age;"
            "person-age \"rajesh\" 25;"
            "attr location string one;"
            "relation assignment name location;"
            "assignment \"rajesh\" \"southlake\";"]
      (let [result (world/process (world/with-input
                                    (start-state)
                                    "person-age ?name 25, assignment ?name \"southlake\";"))]
        (is (ok? result))
        (is (= #{["rajesh" 25 "southlake"]} (-> result :response :body :query-result))))))

  (testing "junction"
    (after ["attr name string one;"
            "attr age long one;"
            "relation person-age name age;"
            "person-age \"douglas\" 25;"
            "person-age \"sarai\"   39;"
            "person-age \"rajesh\"  25;"]
      (let [result (world/process (world/with-input
                                    (start-state)
                                    "person-age ?name ?age, = ?name \"rajesh\";"))]
        (is (ok? result))
        (is (= #{["rajesh" 25]} (-> result :response :body :query-result))))))

  (testing "disjunction"
    (after ["attr name string one;"
            "attr age long one;"
            "relation person-age name age;"
            "person-age \"douglas\" 25;"
            "person-age \"sarai\"   39;"
            "person-age \"rajesh\"  25;"]
      (let [result (world/process (world/with-input
                                    (start-state)
                                    "person-age ?name 25, != ?name \"rajesh\";"))]
        (is (ok? result))
        (is (= #{["douglas" 25]} (-> result :response :body :query-result))))))

  (testing "inequality"
    (after ["attr name string one;"
            "attr age long one;"
            "relation person-age name age;"
            "person-age \"douglas\" 25;"
            "person-age \"sarai\"   39;"
            "person-age \"rajesh\"  25;"]
      (let [result (world/process (world/with-input
                                    (start-state)
                                    "person-age ?name ?age, < ?age 30;"))]
        (is (ok? result))
        (is (= #{["douglas" 25] ["rajesh" 25]} (-> result :response :body :query-result))))
      (let [result (world/process (world/with-input
                                    (start-state)
                                    "person-age ?name ?age, <= ?age 25;"))]
        (is (ok? result))
        (is (= #{["douglas" 25] ["rajesh" 25]} (-> result :response :body :query-result))))
      (let [result (world/process (world/with-input
                                    (start-state)
                                    "person-age ?name ?age, > ?age 30;"))]
        (is (ok? result))
        (is (= #{["sarai" 39]} (-> result :response :body :query-result))))
      (let [result (world/process (world/with-input
                                    (start-state)
                                    "person-age ?name ?age, >= ?age 39;"))]
        (is (ok? result))
        (is (= #{["sarai" 39]} (-> result :response :body :query-result))))))

  (testing "self-joins are allowed"
    (after ["attr a string one;"
            "attr b string one; "
            "relation foo a b;"
            "foo \"cake\" \"pie\";"
            "foo \"cake\" \"cake\";"]
      (let [result (world/process (world/with-input
                                    (start-state)
                                    "foo ?a ?a;"))]
        (is (ok? result))
        (is (= #{["cake"]} (-> result :response :body :query-result)))))))

(deftest query-fields
  (testing "fields are named for the logic variables"
    (after ["attr name string one;"
            "relation location name;"
            "location \"NYC\";"
            "location \"anywhere else\";"]
      (let [result (world/process (world/with-input (start-state) "location ?name;"))]
        (is (ok? result))
        (is (= '[?name] (-> result :response :body :query-fields))))
      (let [result (world/process (world/with-input (start-state) "location ?cake;"))]
        (is (ok? result))
        (is (= '[?cake] (-> result :response :body :query-fields)))))

    (after ["attr name string one;"
            "relation seats name name name name;"
            "seats \"a\" \"b\" \"c\" \"d\";"]
      (let [result (world/process (world/with-input
                                    (start-state)
                                    "seats ?a ?b ?c ?d;"))]
        (is (ok? result))
        (is (= '[?a ?b ?c ?d] (-> result :response :body :query-fields)))))

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
      (let [result (world/process (world/with-input
                                    (start-state)
                                    "person-age ?name ?age, person-title ?name ?title;"))]
        (is (ok? result))
        (is (= #{["douglas" 25 "associate"]
                 ["sarai"   39 "staff"]}
              (-> result :response :body :query-result)))
        (is (= '[?name ?age ?title] (-> result :response :body :query-fields)))))))

(deftest fields-are-ordered-not-named
  (testing "an instance's fields are kept separate"
    (after ["attr name string one;"
            "relation seats name name name name;"
            "seats \"a\" \"b\" \"c\" \"d\";"]
      (let [result (world/process (world/with-input
                                    (start-state)
                                    "seats ?a ?b ?c ?d;"))]
        (is (= #{["a" "b" "c" "d"]} (-> result :response :body :query-result)))))))

(deftest colon-is-shorthand-for-repetition
  (testing "in instances"
    (after ["attr id symbol one;"
            "relation diagram id;"
            "diagram: sysml-diagram behavior-diagram requirements-diagram;"]
      (let [result (world/process (world/with-input (start-state)
                                    "diagram ?a;"))]
        (is (= #{['sysml-diagram] ['behavior-diagram] ['requirements-diagram]}
              (-> result :response :body :query-result)))))

    (after ["attr id symbol one;"
            "relation specialize id id;"
            "specialize sysml-diagram: behavior-diagram requirements-diagram structural-diagram;"]
      (let [result (world/process (world/with-input (start-state)
                                    "specialize ?par ?cld;"))]
        (is (= #{'[sysml-diagram behavior-diagram]
                 '[sysml-diagram requirements-diagram]
                 '[sysml-diagram structural-diagram]}
              (-> result :response :body :query-result)))))

    (after ["attr id symbol one;"
            "attr label string one;"
            "relation diagram-kind id label label;"
            "diagram-kind:
               activity-diagram         \"act\" \"Activity Diagram\"
               block-definition-diagram \"bdd\" \"Block Definition Diagram\"
               internal-block-diagram   \"ibd\" \"Internal Block Diagram\"
               package-diagram          \"pkg\" \"Package Diagram\";"]
      (let [result (world/process (world/with-input (start-state)
                                    "diagram-kind ?kind ?tag ?label;"))]
        (is (= 4 (count
                   (-> result :response :body :query-result))))))))

(deftest relations-can-constrain-values
  (testing "values in the confining relation are accepted"
    (after ["attr name string one;"
            "relation direction name;"
            "direction:\"N\" \"NE\" \"E\" \"SE\" \"S\" \"SW\" \"W\" \"NW\";"
            "relation arrow (name in direction);"]
      (let [result (world/process (world/with-input (start-state)
                                    "arrow \"N\";"))]
        (is (ok? result)))))

  (testing "values not in the confining relation are rejected"
    (after ["attr name string one;"
            "relation direction name;"
            "direction:\"N\" \"NE\" \"E\" \"SE\" \"S\" \"SW\" \"W\" \"NW\";"
            "relation arrow (name in direction);"]
      (let [result (world/process (world/with-input (start-state)
                                    "arrow \"to the knee\";"))]
        (is (not (ok? result)))))))
