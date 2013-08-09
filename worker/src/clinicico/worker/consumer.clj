(ns clinicico.worker.consumer
  (:require [taoensso.nippy :as nippy]
            [zeromq.zmq :as zmq]
            [crypto.random :as crypto]
            [clinicico.common.zeromq :as q]
            [clojure.tools.logging :as log])
  (:import [org.zeromq ZMQ ZMQ$PollItem ZLoop ZLoop$IZLoopHandler]))

(def HEARTBEAT-INTERVAL 1000)
(def HEARTBEAT-LIVENESS 3)

(def INTERVAL-INIT 1000)
(def INTERVAL-MAX 32000)

(def context (zmq/context))

(defn- handle-request
  [connection method handler]
  (let
    [socket (@connection :socket)
     [address request] (q/receive-more socket [String zmq/bytes-type])
     response (handler (nippy/thaw request)) ]
    (log/debug "REQ for" address)
    (q/send-frame-delim socket q/MSG-REP method address (nippy/freeze response))
    (q/send-frame-delim socket q/MSG-READY method)))

(defn- poll-handler [connection method handler]
  (fn []
    (let [socket (@connection :socket)
          [msg-type] (q/receive-more socket [Byte])]
      (condp = msg-type
        q/MSG-PONG (do (swap! connection assoc :liveness HEARTBEAT-LIVENESS)
                       (swap! connection assoc :interval INTERVAL-INIT)
                       (log/debug "PONG from router"))
        q/MSG-REQ  (handle-request connection method handler)))
    0))

(defn disconnect-consumer [connection]
  (.close (@connection :socket))
  (swap! connection dissoc :socket)
  (.removePoller (@connection :zloop) (.getItem (@connection :poller) 0)))

(defn connect-consumer
  [connection method handler]
  (let [zloop (@connection :zloop)
        address "tcp://localhost:7740"
        ident (.getBytes (crypto.random/hex 8))
        socket (zmq/socket context :dealer)
        poller (zmq/poller context 1)
        poll-handler (q/zloop-handler (poll-handler connection method handler))]
    (zmq/connect (zmq/set-identity socket ident) address)
    (zmq/register poller socket :pollin)
    (.addPoller (@connection :zloop) (.getItem poller 0) poll-handler (Object.))
    (swap! connection assoc :socket socket)
    (swap! connection assoc :poller poller)
    (swap! connection assoc :liveness HEARTBEAT-LIVENESS)
    (q/send-frame-delim socket q/MSG-READY method)
    (log/debug "[consumer] connected to router:" (String. ident))
    connection))

(defn- incr-interval [interval]
  (if (> (* 2 interval) INTERVAL-MAX) interval (* 2 interval)))

(defn- reconnect-handler [connection method handler]
  (fn []
    (swap! connection assoc :interval (incr-interval (@connection :interval)))
    (connect-consumer connection method handler)
    0))

(defn- heartbeat-handler [connection method handler]
  (fn [] (if (contains? @connection :socket)
           (do (swap! connection assoc :liveness (dec (@connection :liveness)))
               (if (> (@connection :liveness) 0)
                 (q/send-frame-delim (@connection :socket) q/MSG-PING method)
                 (let [reconnect-handler (q/zloop-handler (reconnect-handler connection method handler))]
                   (log/debug "[consumer] connection to router lost; reconnect in" (@connection :interval))
                   (disconnect-consumer connection)
                   (.addTimer (@connection :zloop) (@connection :interval) 1 reconnect-handler (Object.))
                   )))
           ) 0))

(defn start
  "Starts a consumer in a separate thread"
  [method handler]
  (.start (Thread.
            (fn []
              (let [zloop (ZLoop.)
                    connection (connect-consumer (atom {:zloop zloop :interval INTERVAL-INIT}) method handler)
                    heartbeat-handler (q/zloop-handler (heartbeat-handler connection method handler))]
                (.addTimer zloop HEARTBEAT-INTERVAL 0 heartbeat-handler (Object.))
                (.start zloop))))))
