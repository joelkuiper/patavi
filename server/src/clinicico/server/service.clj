(ns clinicico.server.service
  (:gen-class)
  (:require [clojure.tools.logging :as log]
            [clojure.core.async :as async :refer :all]
            [clinicico.common.zeromq :as q]
            [clinicico.common.util :refer [now]]
            [zeromq.zmq :as zmq]
            [crypto.random :as crypto]
            [clinicico.server.network.broker :as broker]
            [clinicico.server.store :as status]
            [clinicico.server.config :refer [config]]
            [taoensso.nippy :as nippy]))

(def ^:private context (zmq/context 3))
(def ^:private frontend-address (:broker-frontend-socket config))

(defn- save-results!
  [results]
  (let [id (results :id)
        old-status (status/retrieve id)
        new-status {:status "completed"
                    :results {:body (results :results)
                              :files (map (fn [f] {:name (get f "name")
                                                  :mime (get f "mime")})
                                          (results :files))}}]
    (status/save-files! id (get results :files {}))))

(defn- save-update!
  [content]
  (let [id (:id content)
        old-status (or (status/retrieve id) {})
        new-status (merge old-status (get content :content {}))]
    (status/update! id new-status)))

(defn psi [alpha beta]
  "Infinite Stream function. Starts two go routines, one perpetually pushing
   using a function with no arguments (alpha), and one processing then with
   a function taking the channel output as argument (beta)."
  (let [c (chan)]
    (go (loop [x (alpha)]
          (>! c x)
          (recur (alpha))))
    (go (loop [y (<! c)]
          (beta y)
          (recur (<! c))))))

(defn- update-reciever
  [id]
  (let [updates (chan)
        on-update (fn [u] (let [update (nippy/thaw (first u))]
                            (save-update! update)
                            (go (>! updates update))))
        socket (zmq/socket context :sub)]
    (zmq/bind (zmq/subscribe socket "") (:updates-socket config))
    (psi #(q/receive! socket [zmq/bytes-type]) on-update)
    updates))

(defn initialize
  []
  (broker/start frontend-address (:broker-backend-socket config)))

(defn task-available?
  [method]
  (broker/service-available? method))

(defn status
  [id]
  (status/retrieve id))

(defn- process
  "Sends the message and returns the results"
  [msg]
  (let [{:keys [method id]} msg
        socket (q/create-connected-socket context :req frontend-address id)]
    (q/send! socket [method (nippy/freeze msg)])
    (cons id (q/receive! socket 2 zmq/bytes-type))))

(defn- handle-completion
  "Handles the results from the worker,
   saves if nessecary or recovers from error"
  [[id status result]]
  (if (q/status-ok? status)
    (let [results (nippy/thaw result)]
      (if (= (:status results) "failed")
        (status/update! id results)
        (save-results! results)))
    (status/update! id {:status "failed" :cause (String. result)})))

(defn publish
  [method payload]
  (let [id (crypto.random/url-part 6)
        updates (update-reciever id)
        msg {:id id :body payload :method method}
        work (chan)]
    (log/debug (format "Publishing task to %s" method))
    (status/insert! id {:id id
                        :method method
                        :status "pending"
                        :created (now)})
    (go (>! work (process msg)))
    (go (handle-completion (<! work)))
    {:updates updates :id id :status (status/retrieve id)}))
