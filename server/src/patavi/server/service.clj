(ns patavi.server.service
  (:require [clojure.tools.logging :as log]
            [clojure.core.async :as async :refer :all]
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

(defn initialize
  []
  (broker/start frontend-address
                (:broker-backend-socket config)
                updates-address))

(defn- updates-stream
  [id]
  (let [context (zmq/context)
        socket (zmq/socket context :sub)
        updates (chan)]
    (zmq/subscribe (zmq/connect socket updates-address) id)
    (go (loop [msg (zmq/receive-all socket)]
          (let [[_ _ content] msg
                update (nippy/thaw content)]
            (if (= update {id "terminate"})
              (do
                (close! updates)
                (.close socket)
                (.term context))
              (do
                (>! updates update)
                (recur (zmq/receive-all socket)))))))
    updates))


(defn available?
  [method]
  (broker/service-available? method))

(defn- process
  "Sends the message and returns the results"
  [msg]
  (let [context (zmq/context)
        {:keys [method id]} msg]
    (with-open [socket (q/create-connected-socket context :req frontend-address id)]
      (q/send! socket [method (nippy/freeze msg)])
      (try
        (let [[status result] (q/receive! socket 2 zmq/bytes-type)]
          (if (q/status-ok? status)
            (let [results (nippy/thaw result)]
              (if (= (:status results) "failed")
                (throw (Exception. (:cause results)))
                results))
            (throw (Exception. (String. result)))))
        (catch Exception e (do (log/error e) (throw e)))))))

(defn publish
  [method payload]
  (let [id (crypto.random/url-part 6)
        updates (updates-stream id)
        msg {:id id :body payload :method method}]
    (go (>! updates {:status "pending"
                     :created (now)}))
    {:updates updates :id id :results (future (process msg))}))
