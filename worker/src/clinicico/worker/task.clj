(ns clinicico.worker.task
  (:require [langohr.core      :as rmq]
            [langohr.channel   :as lch]
            [langohr.queue     :as lq]
            [langohr.consumers :as lc]
            [langohr.basic     :as lb]
            [taoensso.nippy :as nippy]
            [clojure.string :as s :only [blank?]]
            [cheshire.core :refer :all :as json]
            [clojure.tools.logging :as log]))

(def ^{:const true :private true}
  incoming "clinicico.tasks")

(def ^{:const true :private true}
  outgoing "clinicico.updates")

(defonce ^:private conn (rmq/connect))

(defn update!
  ([id]
   (update! id {} "update"))
  ([id content]
   (update! id content "update"))
  ([id content type]
   (with-open [ch (lch/open conn)]
     (let [msg {:content content :id id :type type}]
       (lb/publish ch outgoing
                   type (json/encode-smile msg)
                   :content-type "application/x-jackson-smile")))))

(defn- task-handler
  [task-fn]
  (fn
    [ch {:keys [content-type delivery-tag type routing-key] :as meta} ^bytes payload]
    (let [msg (nippy/thaw-from-bytes payload)
          id (:id msg)]
      (try
        (let [body (assoc (json/decode (:body msg) true) :id id)
              callback (fn [msg] (when (not (s/blank? msg)) (update! id {:progress msg})))]
          (log/debug (format "Recieved task %s for %s with body %s"
                             id routing-key body))
          (update! id {:status "processing" :accepted (java.util.Date.)})
          (task-fn routing-key body callback)
          (update! id {:status "completed" :completed (java.util.Date.) :results true :progress "done"}))
        (catch Exception e (update! id {:status "failed" :progress "none" :cause (.getMessage e)}))))))

(defn- start-consumer
  "Starts a consumer in a separate thread"
  [conn ch method handler]
  (let [thread (Thread.
                 (fn []
                   (lc/subscribe ch method handler :auto-ack true)))]
    (.start thread)))

(defn initialize
  [method n task]
  (dotimes [n n]
    (let [ch (lch/open conn)
          q (.getQueue
              (lq/declare ch method :durable true :exclusive false :auto-delete true))
          handler (task-handler task)]
      (log/info (format "[main] Connected worker %d. Channeld id: %d for channel %s" (inc n) (.getChannelNumber ch) method))
      (lq/bind ch q incoming :routing-key method)
      (start-consumer conn (lch/open conn) q handler)
      (rmq/close ch))))
