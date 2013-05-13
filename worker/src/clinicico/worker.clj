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
  ([id status]
   (broadcast-update id status nil "update"))
  ([id status message]
   (broadcast-update id status message "update"))
  ([id status message type]
   (with-open [ch (lch/open conn)]
     (let [msg {:content {:status status :message message} :id id :type type}]
       (lb/publish ch outgoing type (json/encode-smile msg) :content-type "application/x-jackson-smile")))))

(defn task-handler
  [ch {:keys [content-type delivery-tag type] :as meta} ^bytes payload]
  (let [task (json/parse-smile payload true)]
    (log/debug
      (format "[consumer] Received a message: %s, delivery tag: %d, content type: %s, type: %s" task delivery-tag content-type type))
    (Thread/sleep 6000)
    (broadcast-update (:id task) "completed")))

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
        (start-consumer conn ch q)))
      (while true (Thread/sleep 100))
      (rmq/close conn)))
