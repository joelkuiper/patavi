(ns cliniccio.test.middleware
  (:use clojure.test
        ring.mock.request
        ring.middleware.session  
        cliniccio.middleware)
  (:import [java.util Date]))

(deftest test-wrap-exception-handler
  (testing "Exception Handling"
    (let [response ((wrap-exception-handler
                      #(throw (Exception. "Testing, 123")))
                      (request :get "/api"))]
      (is (= (response :status) 500))
      (is (instance? Exception (response :body))))))
