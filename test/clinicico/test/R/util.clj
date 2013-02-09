(ns clinicico.test.R.util
  (:use clojure.test
        clinicico.mtc.mtc)
  (:require [clinicico.R.util :as R]))

(def R (atom nil))
(defn r-fixture [f]
  (reset! R (R/connect))
  (f))

(use-fixtures :once r-fixture)

(deftest test-has-r-connection
  (testing "Connection to RServe"
    (is (= (.isConnected @R) true))
    (is (= (R/parse @R "'package:Cairo' %in% search()")) true)
    (is (= (R/parse @R "'package:foo' %in% search()")) false)
    (is (= (count (R/parse @R "rnorm(100)")) 100))))

(deftest test-transforms
  (testing "Transform primitve" 
    (is (= "foo" (R/into-clj (R/into-r "foo"))))
    (is (= 1.0 (R/into-clj (R/into-r 1.0))))
    (is (= 1 (R/into-clj (R/into-r 1))))
    (is (= [1 2] (R/into-clj (R/into-r [1 2]))))
    (is (= [true false] (R/into-clj (R/into-r [true false]))))
    (is (= ["foo" "bar"] (R/into-clj (R/into-r ["foo" "bar"]))))
    (is (= true (R/into-clj (R/into-r true)))))
  (testing "Transformation of RList to map"
    (is (= {"foo" "bar"} (R/into-clj (R/into-r-list {"foo" "bar"}))))
    (is (= {"foo" 12} (R/into-clj (R/into-r-list {"foo" 12}))))
    (is (= {"foo" 12.03} (R/into-clj (R/into-r-list {"foo" 12.03}))))
    (is (= {"foo" "bar" "baz" "qux"} (R/into-clj (R/into-r-list {"foo" "bar" "baz" "qux"})))))
  (testing "Transform nested RList to map"
    (is (= {"foo" {"qux" "bar"}} (R/into-clj (R/into-r-list {"foo" {"qux" "bar"}}))))
    (is (= {"foo" {"qux" false}} (R/into-clj (R/into-r-list {"foo" {"qux" false}}))))
    (is (= {"foo" {"1" {"a" false}}} (R/into-clj (R/into-r-list {"foo" {1 {"a" false}}}))))
    (is (= {"foo" {"1" {"k" "v"}}} (R/into-clj (R/into-r-list {"foo" {1 {:k "v"}}}))))))

(deftest test-r-conversions
  (testing "Assignments to RServe"
    (R/assign @R "foo" {:foo "foobar"}) 
    (is (= (R/rget @R "foo") {"foo" "foobar"})))
  (testing "Complexer assignment"
    (R/assign @R "foo" {:foo [1 2 3 4]}) 
    (is (= (R/rget @R "foo") {"foo" [1 2 3 4]})))) 
