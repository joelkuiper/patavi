(ns cliniccio.handler
  (:use compojure.core
        ring.util.response
        cliniccio.middleware
        cliniccio.http
        [ring.middleware.format-response :only [wrap-restful-response]])
  (:require [compojure.handler :as handler]
            [compojure.route :as route]))

(defroutes api-routes
  (context "/api" []
    (OPTIONS "/" []
      (http/options [:options] {:version "0.1.0-SNAPSHOT"}))
    (ANY "/" [] 
      (http/method-not-allowed [:options]))
    (context "/mtc" []
      (GET "/" []
        (http/not-implemented))
      (GET "/:id" [id]
        (http/not-implemented))
      (HEAD "/:id" [id]
        (http/not-implemented))
      (POST "/" [:as req]
        (http/not-implemented))
      (PUT "/:id" [id]
        (http/not-implemented))
      (DELETE "/:id" [id]
        (http/not-implemented))
      (OPTIONS "/" []
        (http/options [:options :get :head :put :post :delete]))
      (ANY "/" []
        (http/method-not-allowed [:options :get :head :put :post :delete]))))
  (route/not-found "Nothing to see here, move along now"))

(def app
  (->
    (handler/api api-routes)
    (wrap-request-logger)
    (wrap-exception-handler)
    (wrap-response-logger)
    (wrap-restful-response)))
