(ns patavi.worker.consumer
  (:require [taoensso.nippy :as nippy]
            [zeromq.zmq :as zmq]
            [clojure.core.async :refer [go >! <! close! chan]]
            [patavi.common.zeromq :as q]
            [patavi.common.util :refer [insert]]
            [patavi.worker.config :refer [config]]
            [clojure.tools.logging :as log])
  (:import [org.zeromq ZLoop]))

(def ^:const heartbeat-interval (:heartbeat-interval config))
(def ^:const heartbeat-liveness (:expire-broker-after config))

(def ^:const interval-init (:initial-reconnect-interval config))
(def ^:const interval-max (:maximum-reconnect-interval config))

(def ^:const failed "failed")

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
        pong (fn [_ _] (do (reset! reconnecter (create-reconnecter consumer))
                           (swap! consumer assoc :liveness heartbeat-liveness)))
        ping #(q/send! (@consumer :socket) [q/MSG-PING (@consumer :service)] :prefix-empty)
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

(defn- send-update!
  [socket service id content]
  (q/send! socket [q/MSG-UPDATE service id (nippy/freeze (or content {}))] :prefix-empty))

(defn- wrap-exception
  [fn & params]
  (try
    (apply fn params)
    (catch Exception e
      (do (log/warn e)
          {:status failed
           :cause (.getMessage e)}))))

(defn- handle-request
  [consumer handler]
  (fn [_ msg]
    (let [{:keys [service socket]} @consumer
          work (chan)
          [address request] (q/retrieve-data msg [String zmq/bytes-type])
          {:keys [id body] :as content} (nippy/thaw request)
          updater (partial send-update! socket service id)]
      (go (>! work (wrap-exception handler body updater)))
      (go
       (q/send! socket [q/MSG-REP service address (nippy/freeze (<! work))] :prefix-empty)
       (q/send! socket [q/MSG-READY service] :prefix-empty)
       (close! work)))))

(defn- handle-incoming
  [consumer]
  (fn []
    (let [socket (@consumer :socket)
          msg (q/receive! socket)
          [msg-type] (q/retrieve-data msg [Byte] :drop-first)]
      (doall
        (map (fn [[type handler]]
               (when (= msg-type type) (handler consumer msg)))
             (@consumer :handlers))))))

(defn create-consumer
  [service handler]
  (let [context (zmq/context)
        zloop (ZLoop.)
        consumer (atom {:handlers  {}
                        :service service
                        :zloop zloop
                        :socket nil})
        initialize #(let [poller (zmq/poller context 2)
                          socket (q/create-connected-socket
                                  context :dealer (:broker-socket config))]
                      (swap! consumer assoc
                             :socket socket
                             :poller poller)
                      (zmq/register poller socket :pollin)
                      (.addPoller (@consumer :zloop)
                                  (.getItem poller 0)
                                  (q/zloop-handler (fn [] ((handle-incoming consumer)) 0))
                                  {})
                      (q/send! socket [q/MSG-READY service] :prefix-empty))]
    (swap! consumer assoc
           :handlers {q/MSG-REQ (handle-request consumer handler)}
           :initialize initialize)
    ((@consumer :initialize))

    ; Mysterious hack required to start the zloop
    (.addTimer zloop 1000 0 (q/zloop-handler #(do (Thread/sleep 1) 0)) {})
    (.start (Thread. #(.start zloop)))
    consumer))

(defn start
  [service handler]
  (with-heartbeat (create-consumer service handler)))
