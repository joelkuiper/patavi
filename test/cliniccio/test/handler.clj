(ns cliniccio.test.handler
  (:use clojure.test
        ring.mock.request  
        cliniccio.handler))

(deftest test-api-routes
  (testing "API Options"
    (let [response (api-routes (request :options "/api"))]
      (is (= (response :status) 200))
      (is (contains? (response :body) :version))))
  (testing "API Get"
    (let [response (api-routes (request :get "/api"))]
      (is (= (response :status) 405))
      (is (nil? (response :body)))))
  (testing "Not Found"
    (let [response (api-routes (request :get "/invalid"))]
      (is (= (response :status) 404))))
  (testing "Analyze mtc file"
    (let [response (api-routes (request :post "/api/mtc/analyze/file"))]
      (is (= (response :status) 200)))))