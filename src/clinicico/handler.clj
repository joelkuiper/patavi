;; ## Clinici.co 
;; Clinici.co provides a way for exposing R scripts as a web service using 
;; [REST](http://en.wikipedia.org/wiki/Representational_state_transfer).
;; Currently this framework handles the loading and running of GeMTC consistency
;; models, but should eventually not be restricted to this. 
;;
;; The typical life time of a Clinici.co session is roughly as follows: 
;;
;; 1. The users submits JSON or a file with optional options to the appropriate analysis path using `POST`. 
;;    In this case for example a GeMTC file to `/api/consistency` for consistency
;;    analysis.
;; 2. The user recieves a job url.
;; 3. The user can query the job for status using `GET` on `/job/:id` to see for
;;    example the position in the job queue. 
;; 4. When the results are available they are saved to a MongoDB instance as-is.
;; 5. The results can be retrieve using a `GET` on `/result/:id` where the id was
;;    provided by the job when the results became available.
;; 
;; For every session the list of submitted jobs is stored in a map, this allows
;; the user to retrieve the jobs he or she submitted but not others. 
;;

(ns clinicico.handler
  (:use compojure.core
        [clinicico.config]
        [clinicico.util.util]
        [ring.middleware.session]
        [clinicico.middleware]
        [ring.middleware.format-response :only [wrap-restful-response 
                                                wrap-json-response]])
  (:require [compojure.handler :as handler]
            [ring.util.response :as resp]
            [clinicico.http :as http]
            [clinicico.mtc.store :as db]
            [clinicico.jobs :as job]
            [clinicico.mtc.mtc :as mtc]
            [ring.middleware [multipart-params :as mp]]
            [ring.middleware.json :as ring-json]
            [clojure.tools.logging :as log]
            [compojure.route :as route]))

(defn main [] ())

;; ### Consistency models 
;; Consistency models can be created based on a GeMTC network file
;; in a multi-part form submit or, alternatively, by providing
;; a network field in the following JSON format: 
;; 
;;     {"description":"",
;;       "data":[
;;         {
;;           "study":"1",
;;           "treatment":"A",
;;           "mean":-1.22,
;;           "std.dev":3.7,
;;           "sampleSize":54
;;         },
;;         {
;;           "study":"1",
;;           "treatment":"B",
;;           "mean":-1.2,
;;           "std.dev":4.3,
;;           "sampleSize":81
;;         },
;;         {
;;           "study":"2",
;;           "treatment":"B",
;;           "mean":-1.8,
;;           "std.dev":2.48,
;;           "sampleSize":154
;;         },
;;         {
;;           "study":"2",
;;           "treatment":"B",
;;           "mean":-2.1,
;;           "std.dev":2.99,
;;           "sampleSize":143
;;         }
;;       ],
;;       "treatments":[
;;         {
;;           "id":"A",
;;           "description":"Some medicine"
;;         },
;;         {
;;           "id":"B",
;;           "description":"Placebo"
;;         },
;;       ]
;;     } 
;;
;; Where each of the treatments must be present in the data and
;; vice-versa. Furthermore for dichotomous networks the `mean` and
;; `sampleSize` can be replaced by the field `responders`. 
;; The description is optional. The excerpt above specifies a network with
;; continuous data for two two-armed studies comparing treatments
;; A and B. 
 
(defroutes routes-handler
  (context "/api" []
           (OPTIONS "/" []
                    (http/options [:options] {:version "0.3.0-SNAPSHOT"}))
           (mp/wrap-multipart-params
            (POST "/analysis/consistency" [:as req & params]
                   (let [analysis (fn [] (db/save-result (mtc/consistency params)))
                         id (job/submit analysis)
                         jobs (get-in req [:session :jobs])
                         job (str api-url "/job/" id)]
                     (assoc-in 
                       (http/created job (job/status id)) [:session :jobs] (conj jobs id)))))
           (context "/result" []
                    (GET "/:id" [id] (http/no-content? (db/get-result id))))
           (context "/job" [] 
                    (GET "/session" [:as req] 
                         (-> (resp/response (map job/status (get-in req [:session :jobs])))
                             (resp/status 200)))
                    (DELETE "/:id" [id]
                         (do (job/cancel id) 
                            (-> (resp/response (job/status id))
                                (resp/status 200))))
                    (GET "/:id" [id] 
                         (-> (resp/response (job/status id))
                             (resp/status 200)))))

  ;; These routes should be handled by a webserver (e.g. nginx or apache)
  (GET "/" [] (resp/resource-response "index.html" {:root "public"}))
  (route/resources "/generated" {:root "generated"})
  (route/resources "/")
  (route/not-found "Nothing to see here, move along now"))

(def app
  "Main entry point for all requests."
  (->
    (handler/api routes-handler)
    (ring-json/wrap-json-body)
    (wrap-request-logger)
    (wrap-session);
    (wrap-exception-handler)
    (wrap-response-logger)
    (wrap-json-response)
    (wrap-restful-response)))
