(ns clinicico.worker.consumer
  (:gen-class)
  (:require [taoensso.nippy :as nippy]
            [zeromq.zmq :as zmq]
            [clinicico.common.zeromq :as q]
            [clinicico.common.util :refer [insert]]
            [clojure.tools.logging :as log])
  (:import [org.zeromq ZLoop]))

(def ^:const heartbeat-interval 1000)
(def ^:const heartbeat-liveness 3)

(def ^:const interval-init 1000)
(def ^:const interval-max 32000)

(defprotocol Protocol
  (send! [this socket messages]))

(defrecord MessageProtocol [method]
  Protocol
  (send! [this socket messages]
    (apply (partial q/send-frame-delim socket)
       (insert messages 1 (.getBytes (get this :method))))))

(defn- create-reconnecter
  [consumer]
  (let [interval (atom interval-init)
        reconnect (q/zloop-handler
                   (fn []
                     (do
                       (reset! interval (min (* 2 @interval) interval-max))
                       ((@consumer :initialize))
                       (swap! consumer assoc :liveness heartbeat-liveness)) 0))]
    (fn []
      (log/warn "[consumer] connection to router lost; reconnecting in" @interval)
      (.close (@consumer :socket))
      (swap! consumer dissoc :socket)
      (.removePoller (@consumer :zloop) (.getItem (@consumer :poller) 0))
      (.addTimer (@consumer :zloop) @interval 1 reconnect {}))))

(defn- with-heartbeat
  [consumer]
  (let [reconnecter (atom (create-reconnecter consumer))
        pong (fn [_] (do (reset! reconnecter (create-reconnecter consumer))
                        (swap! consumer assoc :liveness heartbeat-liveness)))
        ping #(send! (@consumer :protocol) (@consumer :socket) [q/MSG-PING])
        heartbeat (fn []
                    (when (contains? @consumer :socket)
                      (let [next-liveness (dec (get @consumer :liveness heartbeat-liveness))]
                        (swap! consumer assoc :liveness next-liveness)
                        (if (pos? next-liveness)
                          (ping)
                          (@reconnecter)))))]
    (swap! consumer assoc :handlers (merge (@consumer :handlers) {q/MSG-PONG pong}))
    (.addTimer (@consumer :zloop) heartbeat-interval 0
               (q/zloop-handler #(do (heartbeat) 0)) {})))

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
        zloop (ZLoop.)
        consumer (atom {:handlers  {}
                        :protocol protocol
                        :zloop zloop
                        :socket nil})
        initialize #(let [poller (zmq/poller context 1)
                          socket (q/create-connected-socket
                                  context :dealer "tcp://localhost:7740")]
                      (swap! consumer assoc :socket socket :poller poller)
                      (zmq/register poller socket :pollin)
                      (.addPoller (@consumer :zloop)
                                  (.getItem poller 0)
                                  (q/zloop-handler (fn [] ((handle-incoming consumer)) 0))
                                  {})
                      (send! protocol socket [q/MSG-READY]))]
    (swap! consumer assoc
           :handlers {q/MSG-REQ (handle-request consumer handler)}
           :initialize initialize)
    ((@consumer :initialize))

    ; mysterious hack required to start the zloop
    (.addTimer zloop 1000 0 (q/zloop-handler #(do (Thread/sleep 1) 0)) {})

    (.start (Thread. #(.start zloop)))
    consumer))

(defn start
  [method handler]
  (with-heartbeat (create-consumer method handler)))
