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

(defn main [] ())

(defroutes api-routes
  (context "/api" []
     (mp/wrap-multipart-params 
       (POST "/analysis/consistency" [& params]
             (let [analysis (fn [] (mtc/consistency {:file (get params "file")} {}))
                   id (submit-job analysis)]
               {:status 201 :headers {"Location" (str "/jobs/" id)}})))
     (GET "/jobs/:id" [id]
          (let [job-future (@jobs id)]
            (if (.isDone job-future)
              (.get job-future)
              {:status 404}))))

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
