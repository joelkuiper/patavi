(ns clinicico.server.service
  (:require [clojure.tools.logging :as log]
            [clojure.core.async :as async :refer :all]
            [clojure.string :as s :only [replace]]
            [clinicico.common.zeromq :as q]
            [clinicico.common.util :refer [now]]
            [zeromq.zmq :as zmq]
            [crypto.random :as crypto]
            [clinicico.server.network.broker :as broker]
            [clinicico.server.config :refer [config]]
            [taoensso.nippy :as nippy]))

(def ^:private frontend-address (:broker-frontend-socket config))

(defn initialize
  []
  (broker/start frontend-address (:broker-backend-socket config)))

(defn psi [alpha beta]
  "Infinite Stream function. Starts two go routines, one perpetually pushing
   using a function with no arguments (alpha), and one processing then with
   a function taking the channel output as argument (beta)."
  (let [c (chan)]
    (go (loop [x (alpha)]
          (>! c x)
          (recur (alpha))))
    (go (loop [y (<! c)]
          (when ((comp not nil?) y)
            (beta y)
            (recur (<! c)))))))

(defn- update-reciever
  [id]
  (let [context (zmq/context)
        socket (zmq/socket context :sub)
        updates (chan)]
    (zmq/subscribe (zmq/connect socket (:updates-socket config)) id)
    (psi #(zmq/receive-all socket)
       #(go (>! updates
                (try
                  (nippy/thaw (second %))
                  (catch Exception e {})))))
    updates))

(defn available?
  [method]
  (broker/service-available? method))

(defn- process
  "Sends the message and returns the results"
  [msg]
  (let [context (zmq/context)
        {:keys [method id]} msg
        socket (q/create-connected-socket context :req frontend-address id)]
    (q/send! socket [method (nippy/freeze msg)])
    (try
      (let [[status result] (q/receive! socket 2 zmq/bytes-type)]
        (if (q/status-ok? status)
          (let [results (nippy/thaw result)]
            (if (= (:status results) "failed")
              (throw (Exception. (:cause results)))
              results))
          (throw (Exception. (String. result)))))
      (catch Exception e (do (log/error e) (throw e))))))

(defn publish
  [method payload]
  (let [id (crypto.random/url-part 6)
        channel (update-reciever id)
        msg {:id id :body payload :method method}]
    (go (>! channel {:id id
                     :method method
                     :status "pending"
                     :created (now)}))
    {:updates channel :id id :results (future (process msg))}))
