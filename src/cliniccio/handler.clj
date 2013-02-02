(ns cliniccio.handler
  (:use compojure.core
        [cliniccio.util]
        [cliniccio.middleware]
        [cliniccio.mtc :as mtc]
        [cliniccio.http :as http]
        [cliniccio.R.util :as R]
        ring.middleware.session
        ring.middleware.session.memory
        overtone.at-at
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
    (context "/network" []
      (mp/wrap-multipart-params 
        (POST "/" [:as req]
          (let [R (get-in req [:session :Rserv])]
            (mtc/load-mtc! R) 
            (mtc/load-network-file! R (get-in req [:params "file"]))
            (-> 
              (resp/response (mtc/read-network R))
              (resp/status 200)))))))
    ;(context "/mtc" []
      ;(context "/analyze" []
        ;(mp/wrap-multipart-params 
          ;(POST "/file" [:as req]
              ;(->
                ;(resp/response 
                    ;(let [R (get-in req [:session :Rserv])] 
                      ;(mtc/load-mtc! R) 
                      ;(mtc/analyze-consistency! req (mtc/load-network-file! R (get (:params req) "qqfile")))))
                ;(resp/status 200)))))))

  ;; These routes should be handled by a webserver (e.g. nginx or apache)
  (GET "/" [] (resp/resource-response "index.html" {:root "public"}))
  (route/resources "/generated" {:root "generated"})
  (route/resources "/")
  (route/not-found "Nothing to see here, move along now"))


(def app
  (->
    (handler/api api-routes)
    (wrap-request-logger)
    (wrap-R-session)
    (wrap-session-expiry session-expire) ; tears down Rconnection in the process
    (wrap-session {:store (memory-store sessions)})
    (wrap-exception-handler)
    (wrap-response-logger)
    (wrap-json-response)
    (wrap-restful-response)))
