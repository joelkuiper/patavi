(ns clinicico.test.middleware
  (:use clojure.test
        ring.mock.request
        ring.middleware.session  
        clinicico.middleware)
  (:import [java.util Date]))

(deftest test-wrap-exception-handler
  (testing "Exception Handling"
    (let [response ((wrap-exception-handler
                      #(throw (IllegalArgumentException. "Testing, 123")))
                      (request :get "/api"))]
      (is (= (response :status) 400))
      (is (instance? Exception (response :body))))))
