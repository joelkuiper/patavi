(ns cliniccio.handler
  (:use compojure.core
        [cliniccio.config]
        [cliniccio.util]
        [cliniccio.middleware]
        [cliniccio.jobs]
        [ring.middleware.format-response :only [wrap-restful-response wrap-json-response]])
  (:require [compojure.handler :as handler]
            [ring.util.response :as resp]
            [cliniccio.http :as http]
            [cliniccio.mtc.store :as db]
            [cliniccio.mtc.mtc :as mtc]
            [ring.middleware [multipart-params :as mp]]
            [ring.middleware.json :as ring-json]
            [clojure.tools.logging :as log]
            [compojure.route :as route]))

(defn main [] ())

(defroutes api-routes
  (context "/api" []
           (OPTIONS "/" []
                    (http/options [:options] {:version "0.3.0-SNAPSHOT"}))
           (mp/wrap-multipart-params
             (POST "/analysis/consistency" [& params]
                   (let [analysis (fn [] (db/save-results (mtc/consistency {:file (get params "file")} {})))
                         id (submit-job analysis)
                         job (str base-url "api/results/" id)]
                     (http/created job {:job job}))))
           (context "/results" [] 
                    (GET "/:id" [id]
                         (let [job-future (@jobs id)]
                           (if (.isDone job-future)
                             (-> (resp/response (.get job-future))
                                 (resp/status 200))
                             {:status 204})))))

  ;; These routes should be handled by a webserver (e.g. nginx or apache)
  (GET "/" [] (resp/resource-response "index.html" {:root "public"}))
  (route/resources "/generated" {:root "generated"})
  (route/resources "/")
  (route/not-found "Nothing to see here, move along now"))


(def app
  (->
    (handler/api api-routes)
    (ring-json/wrap-json-body)
    (wrap-request-logger)
    (wrap-exception-handler)
    (wrap-response-logger)
    (wrap-json-response)
    (wrap-restful-response)))
