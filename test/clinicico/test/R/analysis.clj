(ns clinicico.test.R.analysis
  (:use clojure.test
        clinicico.R.analysis
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
