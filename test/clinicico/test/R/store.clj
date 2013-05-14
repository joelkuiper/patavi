(ns clinicico.test.R.store
  (:use clojure.test
        clinicico.R.store
        [monger.core :only [connect! connect set-db! get-db]])
  (:require [clj-time.core :as time]))

(defn mongo-connection [f]
  (connect! { :host "localhost" :port 27017 })
  (set-db! (monger.core/get-db "results-test"))
  (f))

(use-fixtures :once mongo-connection)

;(deftest test-save-result
  ;(testing "Create results"
    ;(let [result {:results "Foo"}
          ;job-info (save-result result)
          ;results (get-result (:id job-info))]
      ;(is (map? results))
      ;(is (contains? results :results))
      ;(is (contains? results :created))
      ;(is (contains? results :modified))))
  ;(testing "Create Invalid Result"
    ;(is (thrown? IllegalArgumentException (save-result {}))))
  ;(testing "Save file into db")
    ;(let [file (doto (java.io.File/createTempFile "tmp" ".stuff") .deleteOnExit)]
      ;(spit file "foobar")
      ;(println "Foobar")
      ;(save-result {:results [{:data {:content file :mime "text/plain"} :name "fooplot"}
                             ;{:data [1 2 3] :name "fooinfo"}]})))
