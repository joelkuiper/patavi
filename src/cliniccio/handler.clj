(ns cliniccio.handler
  (:use compojure.core
        [cliniccio.config]
        [cliniccio.util]
        [cliniccio.middleware]
        [cliniccio.mtc :as mtc]
        [cliniccio.jobs :as job]
        [cliniccio.R.util :as R]
        [ring.middleware.format-response :only [wrap-restful-response wrap-json-response]])
  (:require [compojure.handler :as handler]
            [ring.util.response :as resp]
            [ring.middleware [multipart-params :as mp]]
            [clojure.tools.logging :as log]
            [compojure.route :as route]))

(defn main [] 
  (job/run))

(defroutes api-routes
  (context "/api" []
    (mp/wrap-multipart-params 
      (POST "/analysis/consistency" [:as req]
        (-> 
          (resp/response 
            (job/schedule! (fn [] (mtc/consistency {:file (get-in req [:params "file"])} {}))))
          (resp/status 201))))
      (GET "/job/:uuid" [uuid]
        (-> 
          (resp/response (job/status uuid))
          (resp/status 200))))   

  ;; These routes should be handled by a webserver (e.g. nginx or apache)
  (GET "/" [] (resp/resource-response "index.html" {:root "public"}))
  (route/resources "/generated" {:root "generated"})
  (route/resources "/")
  (route/not-found "Nothing to see here, move along now"))


(def app
  (->
    (handler/api api-routes)
    (wrap-request-logger)
    (wrap-exception-handler)
    (wrap-response-logger)
    (wrap-json-response)
    (wrap-restful-response)))
