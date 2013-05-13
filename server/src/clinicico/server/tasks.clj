(ns clinicico.server.tasks
  (:gen-class)
  (:require [clojure.tools.logging :as log]
            [cheshire.core :as json]
            [clj-time.core :as time]
            [langohr.exchange  :as le]
            [langohr.core      :as rmq]
            [langohr.channel   :as lch]
            [langohr.queue     :as lq]
            [langohr.consumers :as lcm]
            [langohr.consumers :as lc]
            [langohr.basic     :as lb]))

(defonce ^{:private true} conn (rmq/connect))

(def ^{:private true} statuses (atom {}))


(def ^{:const true :private true}
  outgoing "clinicico.tasks")

(def ^{:const true :private true}
  incoming "clinicico.updates")

(defn- update-handler
  [ch metadata ^bytes payload]
  (let [update (json/decode-smile payload true)
        content (into {} (filter (comp not nil? val) (:content update)))
        status (@statuses (:id update))]
    (log/debug (format "[consumer] Received %s" update))
    (swap! statuses assoc (:id update) (merge status content))))

(defn initialize
  []
  (with-open [ch (lch/open conn)]
    (le/declare ch incoming "fanout")
    (le/declare ch outgoing "direct")
    (let [updates (.getQueue (lq/declare ch "update" :exclusive false :auto-delete true))]
      (lq/bind ch updates incoming)
      (lcm/subscribe (lch/open conn) updates update-handler :auto-ack true))))

(defn task-available?
  [method]
  (try
    (with-open [ch (lch/open conn)]
      (and (rmq/open? ch) (> (:consumer-count (lq/status ch method) 0))))
    (catch Exception e
      (do
        (log/error (format "Could not publish task for %s : %s" method (.getMessage e)))
        false))))

(defn status
  [id]
  (let [status (@statuses id)]
    status))

(defn publish-task
  [method payload]
  (with-open [ch (lch/open conn)]
    (log/debug (format "Publishing task to %s (%d workers available)" method (lq/consumer-count ch method)))
    (let [id (str (java.util.UUID/randomUUID))
          msg (assoc payload :id id)]
      (lb/publish ch outgoing method
                  (json/generate-smile msg)
                  :content-type "application/x-jackson-smile" :type "task")
      (swap! statuses assoc id {:id id :status "pending" :created (time/now)})
      (status id))))

