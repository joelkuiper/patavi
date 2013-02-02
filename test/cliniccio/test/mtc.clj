(ns cliniccio.test.mtc
  (:use clojure.test
        cliniccio.mtc)
  (:require [cliniccio.R.util :as R]))

(deftest test-has-r-connection
  (testing "Connection to RServe"
    (let [R (R/connect)]
      (is (.isConnected R))
      (is (-> (.eval R "'package:gemtc' %in% search()") (.isTRUE)))
      (is (-> (.eval R "'package:foo' %in% search()") (.isFALSE)))
      (is (= (count (-> (.eval R "rnorm(100)") (.asDoubles)))) 100))))
