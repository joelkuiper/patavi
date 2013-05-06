(ns clinicico.server.handler
  (:gen-class)
  (:use compojure.core)
  (:require [compojure.handler :as handler]
            [compojure.route   :as route]
            [langohr.core      :as rmq]
            [langohr.channel   :as lch]
            [langohr.queue     :as lq]
            [langohr.consumers :as lc]
            [langohr.basic     :as lb]))

(def ch (lch/open (rmq/connect)))

(def ^{:const true}
  default-exchange-name "")

(def qname
  "langohr.examples.hello-world")

(defn queue-job
  []
    (lb/publish ch default-exchange-name qname "Hello!" :content-type "text/plain" :type "greetings.hi"))

(defroutes app-routes
  (GET "/" [] (queue-job))
  (route/resources "/")
  (route/not-found "Not Found"))

(def app
  (handler/site app-routes))
