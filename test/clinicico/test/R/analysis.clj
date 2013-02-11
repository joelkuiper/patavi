(ns clinicico.test.R.analysis
  (:use clojure.test
        clinicico.R.analysis
        clojure.walk
        validateur.validation)
  (:require [clinicico.R.util :as R]))

(def R (atom nil))
(defn r-fixture [f]
  (reset! R (R/connect))
  (f))

(use-fixtures :once r-fixture)

(deftest test-load-analysis 
  (testing "Load the file"
    (is (thrown? IllegalArgumentException (load-analysis! nil "foo"))))
  (testing "Source in R"
    (load-analysis! @R "echo")
    (is (= true (R/parse @R "exists('echo')")))))

(deftest test-copy-files 
  (testing "Files are copied"
    (let [file (doto (java.io.File/createTempFile "tmp" ".stuff") .deleteOnExit)]
      (spit file "foobar")
      (copy-to-r @R file (.getName file))
      (is (not (= (R/parse @R (str "readLines('"(.getName file)"')")) "foo")))
      (is (= (R/parse @R (str "readLines('"(.getName file)"')"))) "foobar"))))

(deftest test-validate-parameters 
  (testing "Dispatch"
    (testing "Parameters are validated"
      (reset! validators {"foo" (validation-set
                                  (presence-of :foo)
                                  (acceptance-of :bar))})
      (is (thrown? IllegalArgumentException (dispatch "foo" {:cun "q" :xuq "x"}))))
    (testing "Parameters are assigned"
      (is (= {"foo" "bar" "bar" true} (dispatch "echo" {:foo "bar" :bar true}))))
    (testing "Files are copied and retrievable"
     (let [file (doto (java.io.File/createTempFile "tmp" ".stuff") .deleteOnExit)]
      (spit file "foobar")
      (is (= {"foo" {"file" (.getName file)}} (dispatch "echo" {:foo {:file {:tempfile file :filename (.getName file)}}})))))))

(deftest test-run-mtc 
  (testing "Running the GeMTC consistency program" 
    (let [mtc {"network" {"description" "",
       "data" [
         {
           "study" "1",
           "treatment" "A",
           "mean" -1.22,
           "stdDev" 3.7,
           "sampleSize" 54
         },
         {
           "study" "1",
           "treatment" "B",
           "mean" -1.2,
           "stdDev" 4.3,
           "sampleSize" 81
         },
         {
           "study" "2",
           "treatment" "B",
           "mean" -1.8,
           "stdDev" 2.48,
           "sampleSize" 154
         },
         {
           "study" "2",
           "treatment" "A",
           "mean" -2.1,
           "stdDev" 2.99,
           "sampleSize" 143
         },
          {
           "study" "2",
           "treatment" "C",
           "mean" 1.4,
           "stdDev" 8.2,
           "sampleSize" 34
         },
         {
           "study" "3",
           "treatment" "C",
           "mean" -5.1,
           "stdDev" 2.99,
           "sampleSize" 125
         },
         {
           "study" "3",
           "treatment" "B",
           "mean" -1.1,
           "stdDev" 2.99,
           "sampleSize" 177
         }
       ],
       "treatments" [
         {
           "id" "A",
           "description" "Medicine"
         },
         {
           "id" "B",
           "description" "Placebo"
         }
         {
           "id" "C",
           "description" "Medicine2"
         }
       ]
     }}
      results (dispatch "consistency" (keywordize-keys mtc))]
      (is (= (contains? results :results) true)))))


