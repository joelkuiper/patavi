(ns clinicico.server.tasks
  (:gen-class)
  (:require [clojure.tools.logging :as log]
            [cheshire.core :as json]
            [langohr.exchange  :as le]
            [langohr.core      :as rmq]
            [langohr.channel   :as lch]
            [langohr.queue     :as lq]
            [langohr.consumers :as lc]
            [langohr.basic     :as lb]))

(defonce ^{:private true} conn (rmq/connect))

(def ^{:const true :private true}
  exchange-name "")

(defn- qname
  [method]
  (format "task.%s" method))

(defn task-available?
  [method]
  (try
    (let [qname (qname method)]
      (with-open [ch (lch/open conn)]
        (and (rmq/open? ch) (> (:consumer-count (lq/status ch qname) 0)))))
    (catch Exception e
      (do
        (log/error (format "Could not publish task for %s : %s" qname (.getMessage e)))
        false))))

(defn publish-task
  [method payload]
  (let [qname (qname method)]
    (with-open [ch (lch/open conn)]
      (log/debug (format "Publishing task to %s (%d workers available)" qname (lq/consumer-count ch qname)))
      (lb/publish ch exchange-name qname (json/generate-smile payload) :content-type "application/x-jackson-smile" :type "r.task"))
    {:payload payload}))

