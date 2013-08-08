(ns clinicico.worker.task
  (:import [org.zeromq ZMQ ZMQ$PollItem ZLoop ZLoop$IZLoopHandler])
  (:require [taoensso.nippy :as nippy]
            [zeromq.zmq :as zmq]
            [clinicico.common.zeromq :as q]
            [crypto.random :as crypto]
            [clojure.string :as s :only [blank?]]
            [clojure.tools.logging :as log]))

(defonce context (zmq/context 2))
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

(def HEARTBEAT-INTERVAL 1000)

(defn create-heartbeat-handler [socket method]
  (proxy [ZLoop$IZLoopHandler] []
    (handle [^ZLoop _ ^ZMQ$PollItem _ ^Object _]
      (q/send-frame-delim socket q/MSG-PING method) 0)))

(defn create-poll-handler [socket method handler]
  (proxy [ZLoop$IZLoopHandler] []
    (handle [^ZLoop _ ^ZMQ$PollItem _ ^Object _]
      (let [[msg-type] (q/take-more socket [clinicico.common.zeromq.MsgType])]
        (case (:id msg-type)
          3 (log/debug "PONG from router")
          4 (let
              [[address request] (q/take-more socket [String zmq/bytes-type])]
              (log/debug "REQ for" address)
              (q/send-frame-delim socket q/MSG-REP method address (nippy/freeze (handler (nippy/thaw request))))
              (q/send-frame-delim socket q/MSG-READY method))))
      0)))

(defn- start-consumer
  "Starts a consumer in a separate thread"
  [ident method handler]
  (.start (Thread. (fn []
                     (let [socket (zmq/socket context :dealer)
                           zloop (ZLoop.)
                           heartbeat-handler (create-heartbeat-handler socket method)
                           poller (zmq/poller context 1)
                           poll-handler (create-poll-handler socket method handler) ]
                       (zmq/set-identity socket (.getBytes ident))
                       (zmq/connect socket "tcp://localhost:7740")
                       (q/send-frame-delim socket q/MSG-READY method)
                       (.addTimer zloop HEARTBEAT-INTERVAL 0 heartbeat-handler (Object.))
                       (zmq/register poller socket :pollin)
                       (.addPoller zloop (.getItem poller 0) poll-handler (Object.))
                       (.start zloop))))))

(defn initialize
  [method n task-fn]
  (dotimes [n n]
    (let [ident (str method "-" (crypto.random/hex 8))
          handler (task-handler task-fn)]
      (start-consumer ident method handler)
      (log/info (format "[main] Connected worker %s. for %s" ident method)))))

