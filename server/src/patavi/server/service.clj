(ns patavi.server.service
  (:require [clojure.tools.logging :as log]
            [clojure.core.async :refer [go >! <! filter< chan close! mult tap untap]]
            [clojure.string :as s :only [replace]]
            [patavi.common.zeromq :as q]
            [patavi.common.util :refer [now]]
            [zeromq.zmq :as zmq]
            [crypto.random :as crypto]
            [patavi.server.network.broker :as broker]
            [patavi.server.config :refer [config]]
            [taoensso.nippy :as nippy]))

(def ^:private frontend-address (:broker-frontend-socket config))
(def ^:private updates-address (:broker-updates-socket config))

(def context (zmq/context 2))
(def cached-socket
  (memoize q/create-connected-socket))

(def ^:private updates-stream
  ((fn []
     (let [socket (zmq/subscribe (cached-socket context :sub updates-address) "")
           updates (chan)
           mult (mult updates)]
       (go (loop [msg (zmq/receive-all socket)]
             (let [[id _ content] msg
                   update (nippy/thaw content)]
               (>! updates {:msg update :id (String. id)})
               (recur (zmq/receive-all socket)))))
       {:tap (fn [id]
               (let [u (chan)]
                 (filter< (fn [update] (= id (:id update))) (tap mult u))))}))))

(defn initialize
  []
  (broker/start frontend-address (:broker-backend-socket config) updates-address))

(defn available?
  [method]
  (broker/service-available? method))

(defn- process
  "Sends the message and returns the results"
  [msg updates]
  (let [{:keys [method id]} msg]
    (with-open [socket (cached-socket context :req frontend-address id)]
      (q/send! socket [method (nippy/freeze msg)])
      (try
        (let [[status result] (q/receive! socket 2 zmq/bytes-type)]
          (if (q/status-ok? status)
            (let [results (nippy/thaw result)]
              (if (= (:status results) "failed")
                (throw (Exception. (:cause results)))
                results))
            (throw (Exception. (String. result)))))
        (catch Exception e (do (log/error e) (throw e)))
        (finally (close! updates))))))

(defn publish
  [method payload]
  (let [id (crypto.random/url-part 6)
        updates ((:tap updates-stream) id)
        msg {:id id :body payload :method method}]
    (go (>! updates {:status "pending"
                     :created (now)}))
    {:updates updates :id id :results (future (process msg updates))}))
