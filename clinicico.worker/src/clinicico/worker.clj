(ns clinicico.worker
  (:gen-class)
  (:require [langohr.core      :as rmq]
            [langohr.channel   :as lch]
            [langohr.queue     :as lq]
            [langohr.consumers :as lc]
            [langohr.basic     :as lb]))

(def ^{:const true}
  default-exchange-name "")

(defn message-handler
  [ch {:keys [content-type delivery-tag type] :as meta} ^bytes payload]
  (do
    (Thread/sleep 100)
    (println (format "[consumer] Received a message: %s, delivery tag: %d, content type: %s, type: %s"
                     (String. payload "UTF-8") delivery-tag content-type type))))

(defn start-consumer
  "Starts a consumer in a separate thread"
  [conn ch queue-name]
  (let [thread (Thread. (fn []
                          (lc/subscribe ch queue-name message-handler :auto-ack true)))]
    (.start thread)))

(defn -main
  [& args]
  (let [conn  (rmq/connect)
        ch    (lch/open conn)
        qname "langohr.examples.hello-world"]
    (println (format "[main] Connected. Channel id: %d" (.getChannelNumber ch)))
    (lq/declare ch qname :exclusive false :auto-delete true)
    (start-consumer conn ch qname)
    (while true (Thread/sleep 100))
    (rmq/close ch)
    (rmq/close conn)))


