(ns clinicico.worker.consumer
  (:gen-class)
  (:require [taoensso.nippy :as nippy]
            [zeromq.zmq :as zmq]
            [crypto.random :as crypto]
            [clinicico.common.zeromq :as q]
            [clojure.tools.logging :as log])
  (:import [org.zeromq ZMQ ZMQ$PollItem ZLoop ZLoop$IZLoopHandler]))

(def ^:const heartbeat-interval 1000)
(def ^:const heartbeat-liveness 3)

(def ^:const interval-init 1000)
(def ^:const interval-max 32000)

(defprotocol Protocol
  (send! [this socket messages]))

(defn insert [vec pos item] 
    (apply merge (subvec vec 0 pos) item (subvec vec pos)))

(defrecord MessageProtocol [method]
  Protocol
  (send! [this socket messages]
    (apply (partial q/send-frame-delim socket) (insert messages 1 (.getBytes (get this :method))))))

(defn create-socket
  ([context type address]
   (create-socket context type address (crypto.random/hex 8)))
 ([context type address ident]
    (log/debug "[consumer] connecting a socket with identity" ident)
    (zmq/connect
      (zmq/set-identity
        (zmq/socket context type) (.getBytes ident)) address)))

(defn- create-reconnecter
  [consumer]
  (let [reconnect (q/zloop-handler
                   (fn []
                     (doseq [f [:connect :initialize]]
                       ((get @consumer f))) 0))
        interval (atom interval-init)]
    (fn []
      (.close (@consumer :socket))
      (swap! consumer dissoc :socket)
      (.removePoller (@consumer :zloop) (.getItem (@consumer :poller) 0))
      (.addTimer (@consumer :zloop) @interval 1 reconnect (Object.))
      (reset! interval (max (* 2 @interval) interval-max)))))

(defn- with-heartbeat
  [consumer]
  (let [reconnecter (atom (create-reconnecter consumer))
        pong (fn [_] (do (log/debug "[consumer] pong")
                        (reset! reconnecter (create-reconnecter consumer))
                        (swap! consumer assoc :liveness heartbeat-liveness)))
        ping #(do (log/debug "[consumer] ping!")
                  (send! (@consumer :protocol) (@consumer :socket) [q/MSG-PING]))
        heartbeat (fn [] 
                    (when (contains? @consumer :socket)
                      (let [next-liveness (dec (get @consumer :liveness (inc heartbeat-liveness)))]
                        (swap! consumer assoc :liveness next-liveness)
                        (if (> next-liveness 0)
                          (ping)
                          (@reconnecter)))))]
    (swap! consumer assoc :handlers (merge (@consumer :handlers) {q/MSG-PONG pong}))
    (.addTimer (@consumer :zloop) heartbeat-interval 0 (q/zloop-handler (fn [] (do (heartbeat) (int 0)))) (Object.))
  consumer))

(defn- handle-request
  [consumer handler]
  (fn [_]
    (let [protocol (@consumer :protocol)
          socket (@consumer :socket)
          [address request] (q/receive-more socket [String zmq/bytes-type])
          response (handler (nippy/thaw request))]
      (send! protocol socket [q/MSG-REP address (nippy/freeze response)])
      (send! protocol socket [q/MSG-READY]))))
 
(defn- handle-incoming
  [consumer]
  (fn []
    (let [socket (@consumer :socket)
          [msg-type] (q/receive-more socket [Byte])]
      (doall
        (map (fn [[type handler]]
               (when (= msg-type type) (handler consumer)))
             (@consumer :handlers))))))

(defn create-consumer
  [method handler]
  (let [protocol (MessageProtocol. method)
        context (zmq/context)
        consumer (atom {:handlers  {}
                         :protocol protocol
                         :zloop (ZLoop.) 
                         :poller (zmq/poller context)
                         :socket nil})
        connect #(swap! consumer assoc :socket (create-socket context :dealer "tcp://localhost:7740"))
        initialize #(do
                      (zmq/register (@consumer :poller) (@consumer :socket) :pollin)
                      (.addPoller (@consumer :zloop)
                                  (.getItem (@consumer :poller) 0)
                                  (q/zloop-handler (fn [] ((handle-incoming consumer)) (int 0)))
                                  (Object.))
                      (send! protocol (@consumer :socket) [q/MSG-READY]) consumer)]
    (dosync
     (swap! consumer assoc
            :handlers {q/MSG-REQ (handle-request consumer handler)}
            :initialize initialize
            :connect connect)
     (doseq [f [:connect :initialize]]
       ((get @consumer f))))
    consumer))
  
(defn start
  [method handler]
  (let [consumer (with-heartbeat (create-consumer method handler))
        zloop (@consumer :zloop)
        loop (agent {})]
    (.start (Thread. #(.start zloop)))))
