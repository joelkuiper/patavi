(ns cliniccio.handler
  (:use compojure.core
        cliniccio.util
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

(def at-pool (mk-pool))
(def session-expire 3600) ;Time in seconds for maximum length of session after last request
(def sessions (atom {}))

(defn purge-expired [atm] 
  ;; Purges expired sessions from session-map and closes Rserv connection
  (doall (map (fn [[k v]] 
                (if (expire? (:session_timestamp v) (* 1000 session-expire)) 
                  (do 
                    (.close (:Rserv v))
                    (swap! atm dissoc k)))) @atm)))

;; Check every 10 minutes for expired sessions
(every (* 1000 600) #(purge-expired sessions) at-pool)

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
                      (mtc/consistency req (mtc/load-network-file R (get (:params req) "qqfile")))))
                (resp/status 200)))))
      (OPTIONS "/" []
        (http/options [:options :get :head :put :post :delete]))
      (ANY "/" []
        (http/method-not-allowed [:options :get :head :put :post :delete]))))

  ;; These routes should be directly handled by a server (i.e. nginx, apache)  
  (GET "/" [] (resp/resource-response "index.html" {:root "public"}))
  (route/resources "/generated" {:root "generated"})
  (route/resources "/")
  (route/not-found "Nothing to see here, move along now"))


(def app
  (->
    (handler/api api-routes)
    (wrap-request-logger)
    (wrap-R-session)
    (wrap-session-expiry session-expire) ;; 1 hour, tears down Rconnection in the process
    (wrap-session {:store (memory-store sessions)})
    (wrap-exception-handler)
    (wrap-response-logger)
    (wrap-json-response)
    (wrap-restful-response)))
