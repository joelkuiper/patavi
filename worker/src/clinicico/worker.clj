(ns clinicico.worker
  (:gen-class)
  (:use [clojure.tools.cli :only [cli]])
  (:require [langohr.core      :as rmq]
            [langohr.channel   :as lch]
            [langohr.queue     :as lq]
            [langohr.consumers :as lc]
            [langohr.basic     :as lb]
            [cheshire.core :refer :all :as json]
            [clojure.tools.logging :as log]))

(def ^{:const true :private true}
  incoming "clinicico.tasks")

(def ^{:const true :private true}
  outgoing "clinicico.updates")

(defonce ^{:private true} conn (rmq/connect))

(defn broadcast-update
  ([id]
   (broadcast-update id {} "update"))
  ([id content]
   (broadcast-update id content "update"))
  ([id content type]
   (with-open [ch (lch/open conn)]
     (let [msg {:content content :id id :type type}]
       (lb/publish ch outgoing type (json/encode-smile msg) :content-type "application/x-jackson-smile")))))

(defn task-handler
  [ch {:keys [content-type delivery-tag type] :as meta} ^bytes payload]
  (let [task (json/parse-smile payload true)]
    (broadcast-update (:id task) {:status "processing" :accepted (java.util.Date.)})
    (Thread/sleep 6000)
    (broadcast-update (:id task) {:status "completed" :completed (java.util.Date.)})))

(defn start-consumer
  "Starts a consumer in a separate thread"
  [conn ch method]
  (let [thread (Thread.
                 (fn []
                   (lc/subscribe ch method task-handler :auto-ack true)))]
    (.start thread)))

(defn -main
  [& args]
  (let [[options args banner]
        (cli args
             ["-h" "--help" "Show help" :default false :flag true]
             ["-n" "--nworkers" "The amount of worker threads to start" :default (.availableProcessors (Runtime/getRuntime)) :parse-fn #(Integer. %)]
             ["-m" "--method" "The R method and queue name to execute" :default "echo"])
        method (:method options)]
    (when (or (:help options))
      (println banner)
      (System/exit 0))
    (dotimes [n (:nworkers options)]
      (let [ch (lch/open conn)
            q (.getQueue (lq/declare ch method :durable true :exclusive false :auto-delete true))]
        (log/info (format "[main] Connected worker %d. Channeld id: %d for channel %s" (inc n) (.getChannelNumber ch) method))
        (lq/bind ch q incoming :routing-key method)
        (start-consumer conn (lch/open conn) q)
        (rmq/close ch)))
    (while true (Thread/sleep 100))
    (rmq/close conn)))
