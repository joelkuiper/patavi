(ns clinicico.server.handler
  (:gen-class)
  (:use compojure.core)
  (:require [compojure.handler :as handler]
            [compojure.route   :as route]
            [ring.util.response :as resp]
            [langohr.core      :as rmq]
            [langohr.channel   :as lch]
            [langohr.queue     :as lq]
            [langohr.consumers :as lc]
            [langohr.basic     :as lb]))

(def ch (lch/open (rmq/connect)))

(def ^{:const true}
  exchange-name "")

(defn qname
  [method]
  (format "task.%s" method))

(defn publish-task
  [method payload]
  (println (format "Publishing task to %s" (qname method)))
  (lb/publish ch exchange-name (qname method) payload :content-type "text/plain" :type "greetings.hi"))

(defroutes app-routes
  (context "/api" []
           (POST "/task/:method" [method]
                 (let [job (publish-task method "Hello world")]
                   (->
                     (resp/response nil)
                     (resp/header "Location" job)
                     (resp/status 202)))))
  (route/resources "/")
  (route/not-found "Not Found"))

(def app
  (handler/site app-routes))
