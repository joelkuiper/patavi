(ns clinicico.handler
  (:use compojure.core
        [clinicico.config]
        [clinicico.util]
        [ring.middleware.session]
        [clinicico.middleware]
        [ring.middleware.format-response :only [wrap-restful-response wrap-json-response]])
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

(defroutes api-routes
  (context "/api" []
           (OPTIONS "/" []
                    (http/options [:options] {:version "0.3.0-SNAPSHOT"}))
           (mp/wrap-multipart-params
             (POST "/analysis/consistency" [:as req & params]
                   (let [analysis (fn [] (db/save-result (mtc/consistency params)))
                         id (job/submit analysis)
                         jobs (get-in req [:session :jobs])
                         job (str base-url "api/job/" id)]
                     (assoc-in 
                       (http/created job (job/status id)) [:session :jobs] (conj jobs id)))))
           (context "/result" []
                    (GET "/:id" [id] (http/no-content? (db/get-result id))))
           (context "/job" [] 
                    (GET "/session" [:as req] 
                         (-> (resp/response (map job/status (get-in req [:session :jobs])))
                             (resp/status 200)))
                    (GET "/:id" [id] 
                         (-> (resp/response (job/status id))
                             (resp/status 200)))))

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
    (wrap-session);
    (wrap-exception-handler)
    (wrap-response-logger)
    (wrap-json-response)
    (wrap-restful-response)))
