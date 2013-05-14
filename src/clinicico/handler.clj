;; ## Clinici.co
;; Clinici.co provides a way for exposing R scripts as a web service using
;; [REST](http://en.wikipedia.org/wiki/Representational_state_transfer).
;; Currently this framework handles the loading and running of GeMTC consistency
;; models, but should not be restricted to this.
;;
;; The typical life time of a Clinici.co session is roughly as follows:
;;
;; 1. The users submits JSON or a file with optional options to the appropriate analysis path using `POST`.
;;    In this case for example a GeMTC file to `/api/consistency` for consistency
;;    analysis.
;; 2. The user receives a job url.
;; 3. The user can query the job for status using `GET` on `/job/:id` to see for
;;    example the position in the job queue.
;; 4. When the results are available they are saved to a MongoDB instance as-is.
;; 5. The results can be retrieve using a `GET` on `/result/:id` where the id was
;;    provided by the job when the results became available.
;;

(ns clinicico.handler
  (:use compojure.core
        [clinicico.config]
        [clinicico.util.util]
        [clinicico.middleware]
        [ring.middleware.format-params :only [wrap-restful-params]]
        [ring.middleware.format-response :only [wrap-restful-response]])
  (:require [compojure.handler :as handler]
            [ring.util.response :as resp]
            [clinicico.http :as http]
            [clinicico.jobs :as job]
            [cheshire.core :refer :all]
            [clinicico.R.analysis :as analysis]
            [clinicico.R.store :as db]
            [ring.middleware [multipart-params :as mp]]
            [ring.middleware.json :as ring-json]
            [clojure.tools.logging :as log]
            [compojure.route :as route])
  (:gen-class))

(defn main [] ())

(def ^:dynamic *version* "0.2.1")

(defroutes routes-handler
  (context "/api" []
           (OPTIONS "/" []
                    (http/options [:options] {:version *version*}))
           (OPTIONS "/analysis/:method" []
                    (http/options [:post :options :get] {:version *version*}))
           (POST "/analysis/:method" {body :body {method :method} :params}
                 (let [analysis (fn [] (db/save-result (analysis/dispatch method (parse-string (slurp body)))))
                       id (job/submit analysis)
                       job (str api-url "/job/" id)]
                   (http/created job (job/status id))))
           (context "/result" []
                    (OPTIONS "/:id" []
                             (http/options [:get :options] {:version *version*}))
                    (GET "/:id" [id] (http/no-content? (db/get-result id)))
                    (GET "/:id/file/:analysis/:file" [id analysis file]
                         (let [record (db/get-file id analysis file)]
                           (-> (resp/response (.getInputStream record))
                               (resp/content-type (:content-type (.getContentType record)))
                               (resp/header "Content-Length" (.getLength record))))))
           (context "/job" []
                    (OPTIONS "/:id" []
                             (http/options [:delete :get :options] {:version *version*}))
                    (DELETE "/:id" [id]
                            (do (job/cancel id)
                                (resp/status (resp/response (job/status id)) 200)))
                    (GET "/:id" [id]
                         (resp/status (resp/response (job/status id)) 200))))

  ;; These routes should be handled by a webserver (e.g. nginx or apache)
  (GET "/" [] (resp/resource-response "index.html" {:root "public"}))
  (route/resources "/")
  (route/not-found "Nothing to see here, move along now"))

(defn handle-cors-headers
  [handler]
  (fn [request]
    (let [response (handler request)]
      (-> response
          (resp/header "Access-Control-Allow-Origin" "*")
          (resp/header "Access-Control-Allow-Headers" "content-type, x-requested-with")))))

(def app
  "Main entry point for all requests."
  (->
    (handler/api routes-handler)
    (wrap-request-logger)
    (wrap-exception-handler)
    (wrap-response-logger)
    (handle-cors-headers)
    (wrap-restful-response)))
