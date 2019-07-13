(ns gos.inputstack-test
  (:require  [clojure.test :refer :all]
             [clojure.java.io :as io]
             [gos.instack :as instack]
             [clojure.string :as str])
  (:import clojure.lang.LineNumberingPushbackReader
           java.io.StringReader))

(def test-input1
  ["attr name string one;"
   "attr location string one;"
   "attr no-go date many;"
   ""
   ""
   ""])

(def test-input2
  ["relation component name;"
   "relation location name;"
   "relation system-at name name;"
   "relation before name name;"])

(defn- string-reader [s]
  (LineNumberingPushbackReader. (StringReader. s)))

(defn fake-stdin [ss]
  (instack/mksource "*in*" (string-reader (str/join \newline ss))))

(deftest stdin
  (testing "reports line numbers"
    (let [lines (instack/lines [(fake-stdin test-input1)])]
      (is (= (count lines) (count test-input1)))
      (is (= (mapv :line-number lines) (range 1 (inc (count test-input1))))))))

(deftest multiple-readers
  (let [source1 (fake-stdin test-input1)
        source2 (fake-stdin test-input2)
        lines (instack/lines [source1 source2])]
    (is (= (count lines) (+ (count test-input1) (count test-input2))))
    (is (= (mapv :line-number lines)
          (concat
            (range 1 (inc (count test-input1)))
            (range 1 (inc (count test-input2))))))))

(run-tests)
