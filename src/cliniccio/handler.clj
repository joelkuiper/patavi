(ns cliniccio.handler
  (:use compojure.core
        cliniccio.middleware
        ring.middleware.session
        ring.middleware.session.memory
        overtone.at-at
        [cliniccio.mtc :as mtc]
        [cliniccio.http :as http]
        [cliniccio.R.util :as R]
        [ring.middleware.format-response :only [wrap-restful-response wrap-json-response]])
  (:require [compojure.handler :as handler]
            [ring.util.response :as resp]
            [ring.middleware [multipart-params :as mp]]
            [clojure.tools.logging :as log]
            [compojure.route :as route]))

;; TODO: Purge the sessions every hour 
(def at-pool (mk-pool))
(def sessions (atom {}))
(defn purge-expired [sessions] 
  (doall (map #((log/debug (val %))) sessions)))

(every 1000 #(purge-expired @sessions) at-pool)

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
      (context "/analyze" []
        (mp/wrap-multipart-params 
          (POST "/file" [:as req]
              (->
                (resp/response 
                    (let [R (get-in req [:session :Rserv])] 
                      (mtc/load-mtc! R) 
                      (mtc/consistency (mtc/load-network-file R (get (:params req) "qqfile")))))
                (resp/status 200)))))
      (OPTIONS "/" []
        (http/options [:options :get :head :put :post :delete]))
      (ANY "/" []
        (http/method-not-allowed [:options :get :head :put :post :delete]))))

  (GET "/" [] (resp/resource-response "index.html" {:root "public"}))
  (route/resources "/generated" {:root "generated"})
  (route/resources "/")
  (route/not-found "Nothing to see here, move along now"))


(def app
  (->
    (handler/api api-routes)
    (wrap-request-logger)
    (wrap-R-session)
    (wrap-session-expiry 3600) ;; 1 hour, tears down Rconnection in the process
    (wrap-session {:store (memory-store sessions)})
    (wrap-exception-handler)
    (wrap-response-logger)
    (wrap-json-response)
    (wrap-restful-response)))
