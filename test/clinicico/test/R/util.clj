(ns clinicico.test.R.util
  (:use clojure.test)
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
    (is (= [{"foo" "bar"} {"qun" "qux"}] (R/into-clj (R/into-r [{:foo "bar"} {:qun "qux"}]))))
    (is (= [true false] (R/into-clj (R/into-r [true false]))))
    (is (= ["foo" "bar"] (R/into-clj (R/into-r ["foo" "bar"]))))
    (is (= {"foo" [{"a" "b"} {"c" "d"}]} (R/into-clj (R/into-r {"foo" [{:a "b"} {:c "d"}]}))))
    (is (= true (R/into-clj (R/into-r true)))))
  (testing "Transformation of RList to map"
    (is (= {"foo" "bar"} (R/into-clj (R/into-r {"foo" "bar"}))))
    (is (= {"foo" 12} (R/into-clj (R/into-r {"foo" 12}))))
    (is (= {"foo" 12.03} (R/into-clj (R/into-r {"foo" 12.03}))))
    (is (= {"foo" "bar" "baz" "qux"} (R/into-clj (R/into-r {"foo" "bar" "baz" "qux"})))))
  (testing "Transform nested RList to map"
    (is (= {"foo" {"qux" "bar"}} (R/into-clj (R/into-r {"foo" {"qux" "bar"}}))))
    (is (= {"foo" {"qux" false}} (R/into-clj (R/into-r {"foo" {"qux" false}}))))
    (is (= {"foo" {"1" {"a" false}}} (R/into-clj (R/into-r {"foo" {1 {"a" false}}}))))
    (is (= {"foo" {"1" {"k" "v"}}} (R/into-clj (R/into-r {"foo" {1 {:k "v"}}})))))
  (testing "Transformation of list of maps"
    (is (= [{"foo" 10} {"bar" 5}] (R/into-clj (R/into-r [{"foo" 10} {"bar" 5}]))))
    (is (= {"b" {"a" [{"foo" 10} {"bar" 5}]}} (R/into-clj (R/into-r {"b" {"a" [{"foo" 10} {"bar" 5}]}}))))
    (is (= {"test" [ {"aap" 3 "noot" 4} {"aap" 8 "noot" -5} ]} (R/into-clj (R/into-r {"test" [ {"aap" 3 "noot" 4} {"aap" 8 "noot" -5} ]}))))
    (is (= {"qux" [{"foo" 10} {"bar" 5}]} (R/into-clj (R/into-r {"qux" [{"foo" 10} {"bar" 5}]}))))))

(deftest test-r-assignments
  (testing "Assignments to RServe"
    (R/assign @R "foo" {:foo "foobar"})
    (is (= (R/rget @R "foo") {"foo" "foobar"}))
    (R/assign @R "numbers" '(1 2 3 4))
    (is (= "integer" (R/parse @R (str "class(numbers)"))))
    (R/assign @R "numbers" {:foo '(1 2 3 4)})
    (is (= "integer" (R/parse @R (str "class(numbers$foo)"))))
    (R/assign @R "numbers" {:foo '[1 2 3 4]})
    (is (= "integer" (R/parse @R (str "class(numbers$foo)")))))
  (testing "Complexer assignment"
    (R/assign @R "foo" {:foo [1 2 3 4]})
    (is (= (R/rget @R "foo") {"foo" [1 2 3 4]})))
  (testing "Asignment of list of maps"
    (R/assign @R "foo" [{"foo" 10} {"bar" 5}])
    (is (= (R/rget @R "foo") [{"foo" 10} {"bar" 5}])))
  (testing "Assignment of map list of maps"
    (R/assign @R "foo" {"b" {"a" [{"foo" 10} {"bar" 5}]}})
    (is (= (R/rget @R "foo") {"b" {"a" [{"foo" 10} {"bar" 5}]}})))
  (testing "Assignment of map list of maps"
    (R/assign @R "bar" {"test" [ {"aap" 3 "noot" 4} {"aap" 8 "noot" -5} ]})
    (is (= (R/rget @R "bar") {"test" [ {"aap" 3 "noot" 4} {"aap" 8 "noot" -5} ]}))))

