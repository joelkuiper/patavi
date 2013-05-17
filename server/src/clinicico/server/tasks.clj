(ns clinicico.server.tasks
  (:gen-class)
  (:require [clojure.tools.logging :as log]
            [cheshire.core :as json]
            [clj-time.core :as time]
            [taoensso.nippy :as nippy]
            [langohr.exchange  :as le]
            [langohr.core      :as rmq]
            [langohr.channel   :as lch]
            [langohr.queue     :as lq]
            [langohr.consumers :as lcm]
            [langohr.consumers :as lc]
            [langohr.basic     :as lb]))

(defonce ^{:private true} conn (rmq/connect))

(def ^{:private true} statuses (atom {}))
(def ^{:private true} callbacks (atom {}))

(def ^{:const true :private true}
  outgoing "clinicico.tasks")

(def ^{:const true :private true}
  incoming "clinicico.updates")

(defn- cleanup
  [task-id]
  (do
    (log/debug "Done with task " task-id (@statuses task-id))
    (swap! callbacks dissoc task-id)))

(defn- update-handler
  ([ch metadata]
   (log/debug "[consumer] without payload: " ch " " metadata))
  ([ch metadata ^bytes payload]
   (let [update (json/decode-smile payload true)
         id (:id update)
         content (into {} (filter
                            (comp not nil? val) (:content update)))
         old-status (or (@statuses id) {})
         callback (or (@callbacks id) (fn [_]))]
     (swap! statuses assoc id (merge old-status content))
     (callback (@statuses id))
     (when (contains? #{"failed" "completed"} (:status content)) (cleanup id)))))

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
  [method payload callback]
  (with-open [ch (lch/open conn)]
    (log/debug (format "Publishing task to %s (%d workers available)" method (lq/consumer-count ch method)))
    (let [id (str (java.util.UUID/randomUUID))
          msg {:id id :body payload}]
      (lb/publish ch outgoing method
                  (nippy/freeze-to-bytes msg)
                  :content-type "application/nippy" :type "task")
      (swap! statuses assoc id {:id id :method method :status "pending" :created (time/now)})
      (swap! callbacks assoc id callback)
      (status id))))
