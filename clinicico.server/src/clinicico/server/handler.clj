(ns clinicico.server.handler
  (:gen-class)
  (:use compojure.core
        clinicico.server.middleware
        [ring.middleware.format-response
         :only [wrap-restful-response
                wrap-json-response]])
  (:require [compojure.handler :as handler]
            [compojure.route   :as route]
            [clojure.tools.logging :as log]
            [ring.util.response :as resp]
            [langohr.exchange  :as le]
            [langohr.core      :as rmq]
            [langohr.channel   :as lch]
            [langohr.queue     :as lq]
            [langohr.consumers :as lc]
            [langohr.basic     :as lb]))

(def conn (rmq/connect))

(def ^{:const true}
  exchange-name "")

(defn qname
  [method]
  (format "task.%s" method))

(defn publish-task
  [method payload]
  (try
    (let [qname (qname method)]
      (with-open [ch (lch/open conn)]
        (log/debug (format "Publishing task to %s (%d workers available)" qname (lq/consumer-count ch qname)))
        (lb/publish ch exchange-name qname payload :content-type "text/plain" :type "r.task"))
      {:location "ddf" :success true})
    (catch Exception e {:success false :cause (.getMessage e)})))

(defroutes app-routes
  (context "/api" []
           (POST "/task/:method" [method payload]
                 (let [job (publish-task method payload)]
                   (if (:success job)
                     (-> (resp/response nil)
                         (resp/header "Location" (:location job))
                         (resp/status 202))
                     (-> (resp/response (:cause job))
                         (resp/status 501))))))
  (route/resources "/")
  (route/not-found "Not Found"))

(defn main [] ())

(def app
  (->
    (handler/site app-routes)
    (wrap-request-logger)
    (wrap-exception-handler)
    (wrap-response-logger)
    (wrap-json-response)
    (wrap-restful-response)))
