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
                   (fn [] (doseq [f [(@consumer :connect) (@consumer :initialize)]]
                            (f)) 0))
        interval (atom interval-init)]
    (fn [] 
      (swap! interval (max (* 2 @interval) interval-max))
      (log/debug "[consumer] connection to router lost reconnecting in" @interval)
      (.addTimer (@consumer :zloop) @interval 1 reconnect (Object.)))))

(defn- with-heartbeat
  [consumer]
  (let [reconnecter (atom (create-reconnecter consumer))
        pong (fn [_] (do (log/debug "[consumer] pong")
                         (reset! reconnecter (create-reconnecter consumer))
                         (send consumer assoc :liveness heartbeat-liveness)))
        ping #(do (log/debug "[consumer] ping!") (send! (@consumer :protocol) (@consumer :socket) [q/MSG-PING]))
        heartbeat (fn [] 
                    (when-not (agent-error consumer)
                      (let [next-liveness (dec (get @consumer :liveness (inc heartbeat-liveness)))]
                        (log/debug "[consumer] invoked hearbeat")
                        (send consumer assoc :liveness next-liveness)
                        (if (> next-liveness 0)
                          (ping)
                          (send consumer #(throw (Exception. "Connection to router lost")))))))]
    (send consumer assoc :handlers (merge (@consumer :handlers) {q/MSG-PONG pong}))
    (.addTimer (@consumer :zloop) heartbeat-interval 0 (q/zloop-handler (fn [] (do (heartbeat) (int 0)))) (Object.))
    (set-error-handler! consumer (fn [ag ex]
                                   (log/warn "[client]" (.printStackTrace ex))
                                   (.close (@ag :socket))
                                   (.removePoller (@ag :zloop) (.getItem (@ag :poller) 0))
                                   (restart-agent consumer)
                                   (@reconnecter)))
    consumer))


(defn- handle-request
  [consumer handler]
  (fn []
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
        obj {:handlers  {}
             :protocol protocol
             :zloop (ZLoop.)
             :poller (zmq/poller context)
             :socket nil}
        consumer (agent obj)
        connect #(send-off consumer assoc :socket (create-socket context :dealer "tcp://localhost:7740"))
        initialize #(do
                      (await consumer)
                      (zmq/register (@consumer :poller) (@consumer :socket) :pollin)
                      (.addPoller (@consumer :zloop)
                                  (.getItem (@consumer :poller) 0)
                                  (q/zloop-handler (fn [] ((handle-incoming consumer)) (int 0)))
                                  (Object.))
                      (.start (Thread. (fn [] (.start (@consumer :zloop)))))
                      (send! protocol (@consumer :socket) [q/MSG-READY]) consumer)]
    (send-off consumer assoc
              :handlers {q/MSG-REQ (handle-request consumer handler)}
              :initialize initialize
              :connect connect)
    (await consumer)
    (doseq [f [(@consumer :connect) (@consumer :initialize)]]
      (f))
    (println consumer)
    consumer))
  
(defn start
  [method handler]
  (with-heartbeat (create-consumer method handler)))
