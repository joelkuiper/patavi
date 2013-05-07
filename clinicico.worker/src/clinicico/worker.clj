(ns clinicico.worker
  (:gen-class)
  (:use [clojure.tools.cli :only [cli]])
  (:require [langohr.core      :as rmq]
            [langohr.channel   :as lch]
            [langohr.queue     :as lq]
            [langohr.consumers :as lc]
            [langohr.basic     :as lb]))

(def ^{:const true}
  exchange-name "")

(defn message-handler
  [ch {:keys [content-type delivery-tag type] :as meta} ^bytes payload]
  (do
    (println (format "[consumer] Received a message: %s, delivery tag: %d, content type: %s, type: %s"
                     (String. payload "UTF-8") delivery-tag content-type type))
    (Thread/sleep 2000)))

(defn start-consumer
  "Starts a consumer in a separate thread"
  [conn ch queue-name]
  (let [thread (Thread.
                 (fn []
                   (lc/subscribe ch queue-name message-handler :auto-ack true)))]
    (.start thread)))


(defn -main
  [& args]
  (let [[options args banner]
        (cli args
             ["-h" "--help" "Show help" :default false :flag true]
             ["-n" "--nworkers" "The amount of worker threads to start" :default (.availableProcessors (Runtime/getRuntime)) :parse-fn #(Integer. %)]
             ["-m" "--method" "The R method and queue name to execute" :default "echo"])
        conn (rmq/connect)
        qname (format "task.%s" (:method options))]
    (when (or (:help options))
      (println banner)
      (System/exit 0))
    (dotimes [n (:nworkers options)]
      (let [ch (lch/open conn)]
        (println (format "[main] Connected worker %d. Channeld id: %d for channel %s" n (.getChannelNumber ch) qname))
        (lq/declare ch qname :exclusive false :auto-delete true)
        (start-consumer conn ch qname)))
      (while true (Thread/sleep 100))
      (rmq/close conn)))
