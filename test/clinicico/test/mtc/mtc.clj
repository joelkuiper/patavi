(ns clinicico.test.mtc.mtc
  (:use clojure.test
        clinicico.mtc.mtc)
  (:require [clinicico.R.util :as R]))

(deftest test-has-r-connection
  (testing "Connection to RServe"
    (let [R (R/connect)]
      (is (= (.isConnected R) true))
      (is (= (R/parse R "'package:Cairo' %in% search()")) true)
      (is (= (R/parse R "'package:foo' %in% search()")) false)
      (is (= (count (R/parse R "rnorm(100)")) 100)))))
