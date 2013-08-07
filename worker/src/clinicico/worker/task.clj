(ns clinicico.worker.task
  (:import [org.zeromq ZMQ])
  (:require [taoensso.nippy :as nippy]
            [zeromq.zmq :as zmq]
            [clojure.string :as s :only [blank?]]
            [clojure.tools.logging :as log]))

(defonce context (zmq/context 3))
(def updates (zmq/connect (zmq/socket context :pub) "tcp://localhost:7720"))

(defn update!
  ([id content]
   (update! id content "task"))
  ([id content type]
   (let [update {:content content :id id :type type}]
     (zmq/send updates (nippy/freeze update)))))

(defn- task-handler
  [task-fn]
  (fn
    [task]
    (let [method (:method task)
          id (:id task)]
      (try
        (do
          (log/debug (format "Recieved task %s" id))
          (update! id {:status "processing" :accepted (java.util.Date.)})
          (let [results (task-fn method id (:body task) #(update! id {:progress %}))]
            (update! id {:progress "done"
                         :completed (java.util.Date.)})
            results))
        (catch Exception e
          (update! id {:status "failed"
                       :progress "none"
                       :cause (.getMessage e)})
          nil)))))

(defn- start-consumer
  "Starts a consumer in a separate thread"
  [n method handler]
  (.start (Thread. (fn []
                     (let [ident (str method "-" n)
                           socket (zmq/socket context :req)]
                       (zmq/set-identity socket (.getBytes ident))
                       (zmq/connect socket "tcp://localhost:7740")
                       (zmq/send socket (.getBytes "READY"))
                       (log/debug "READY sent to router by" ident)
                       (while true
                         (let [address (zmq/receive socket)
                               _ (zmq/receive socket)
                               response (handler (nippy/thaw (zmq/receive socket)))]
                           (zmq/send socket address ZMQ/SNDMORE)
                           (zmq/send socket (byte-array 0) ZMQ/SNDMORE)
                           (zmq/send socket (nippy/freeze response)))))))))

(defn initialize
  [method n task-fn]
  (dotimes [n n]
    (let [handler (task-handler task-fn)]
      (start-consumer n method handler)
      (log/info (format "[main] Connected worker %d. for %s" (inc n) method)))))
