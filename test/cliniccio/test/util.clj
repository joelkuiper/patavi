(ns cliniccio.test.util
  (:use clojure.test
        cliniccio.util))


(deftest test-transpose-map
  (testing "Transposing map of lists to list of maps"
    (let [input {:a [1 2 3]
                 :b [4 5 6]
                 :c [7 8 9] 
                 :d nil} 
          expected [{:a 1 :b 4 :c 7}
                    {:a 2 :b 5 :c 8}
                    {:a 3 :b 6 :c 9}]]
      (is (= (transpose-map input) expected)))))
