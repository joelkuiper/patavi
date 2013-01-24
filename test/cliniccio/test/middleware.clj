(ns cliniccio.test.middleware
  (:use clojure.test
        ring.mock.request  
        cliniccio.middleware))

(deftest test-wrap-exception-handler
  (testing "Exception Handling"
    (let [response ((wrap-exception-handler
                      #(throw (Exception. "Testing, 123")))
                      (request :get "/api"))]
      (is (= (response :status) 500))
      (is (instance? Exception (response :body))))))
