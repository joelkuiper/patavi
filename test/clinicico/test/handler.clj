(ns clinicico.test.handler
  (:require [cheshire.core :refer :all])
  (:use clojure.test
        ring.mock.request
        clinicico.handler))

(deftest echo-creates-a-job
  (let [request-body ""
        response (routes-handler
                   (body (request :post "/api/analysis/echo") request-body))]
  (is (= (:status response) 201))
  (is (= (not (nil? (get-in response [:body :job])))))))

(deftest echo-echos-params
  (let [request-body (generate-string {:foo "bar"})
        response (routes-handler
                   (content-type (body (request :post "/api/analysis/echo") "foo") "application/json"))]
  (is (= (:status response) 201))
  (is (= (not (nil? (get-in response [:body :job])))))
  (let [job (do (Thread/sleep 100) (routes-handler (request :get (get-in response [:body :job]))))
        result (routes-handler (request :get (get-in job [:body :results])))
        results (get-in result [:body :results :echo])]
    (println results)
    (println request-body)
    (is false))))
