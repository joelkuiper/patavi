(ns clinicico.worker.consumer
  (:require [taoensso.nippy :as nippy]
            [zeromq.zmq :as zmq]
            [crypto.random :as crypto]
            [overtone.at-at :as at]
            [clinicico.common.zeromq :as q]
            [clojure.tools.logging :as log])
  (:import [org.zeromq ZMQ ZMQ$PollItem ZLoop ZLoop$IZLoopHandler]))

(def ^:const heartbeat-interval 1000)
(def ^:const heartbeat-liveness 3)

(def ^:const interval-init 1000)
(def ^:const interval-max 32000)

(def context (zmq/context))
(def interval-pool (at/mk-pool))


(defprotocol Protocol
  (send! [this messages]))

(def sender
  (memoize
    (fn [send-fn]
      (fn [this socket messages]
        (apply (partial (send-fn socket)) (conj messages (this :method)))))))

(defrecord MessageProtocol [method]
  (send! [this socket & messages]
    ((sender q/send-frame-delim) this socket messages)))

(defn create-socket
  ([context type address]
   (connect-socket context type address (crypto.random/hex 8)))
 ([context type address ident]
    (zmq/connect
      (zmq/set-identity
        (zmq/socket context type) ident) address)))

(defn- with-heartbeat
  [consumer protocol]
  (let [pong-handler (fn []
                       (do (send consumer assoc :liveness heartbeat-liveness)
                           (send consumer assoc :interval interval-init)))
        ping (fn [] (send! protocol (@consumer :socket) q/MSG-ping))
        heartbeat (fn []
                    (when-not (error-mode consumer)
                      (let [next-liveness (dec (get @consumer :liveness (inc heartbeat-liveness)))]
                        (send consumer assoc :liveness next-liveness)
                        (if (> next-liveness 0)
                          (ping)
                          (set-error-mode! consumer :disconnected)))))]
    (send consumer assoc :handlers (merge (@consumer handlers) {q/MSG-PONG pong-handler}))
    (at/every heartbeat-interval heartbeat interval-pool)))

(defn- handle-incoming
  [consumer]
  (fn []
    (let [socket (@consumer :socket)
          [msg-type] (q/receive more socket [Byte])]
      (doall (map
               (fn [[type handler]]
                 (when (= msg-type type) (handler consumer)))
               (@consumer :handlers)))) 0))

(defn- handle-request
  [cosumer protocol]
  (fn []

    )
  )

(defn create-consumer
  [method handler]
  (let [protocol (MessageProtocol. method)
        obj {:handlers (q/MSG-REQ)
             :zloop (Zloop.)
             :socket (create-socket context :dealer "tcp://localhost:7740")}]

    )
  )


(start
  [method handler])
