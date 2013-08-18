(ns clinicico.server.tasks
  (:gen-class)
  (:require [clojure.tools.logging :as log]
            [clojure.core.async :as async :refer :all]
            [clinicico.common.zeromq :as q]
            [zeromq.zmq :as zmq]
            [crypto.random :as crypto]
            [clinicico.server.network.broker :as broker]
            [clinicico.server.store :as status]
            [taoensso.nippy :as nippy]))

(def ^{:private true} callbacks (atom {}))

(def context (zmq/context 3))

(def frontend-address "ipc://frontend.ipc")

(defn- cleanup
  [task-id]
  (do
    (log/debug "[tasks] done with task " task-id)
    (swap! callbacks dissoc task-id)))

(defn save-results!
  [results]
  (let [id (results :id)
        old-status (status/retrieve id)
        new-status {:status "completed"
                    :results {:body (results :results)
                              :files (map (fn [f] {:name (get f "name")
                                                  :mime (get f "mime")})
                                          (results :files))}}]
    (status/save-files! id (get results :files {}))
    (status/update! id (merge old-status new-status))))

(defn- update!
  [content]
  (let [id (:id content)
        old-status (or (status/retrieve id) {})
        new-status (merge old-status (get content :content {}))]
    (status/update! id new-status)
    ((get @callbacks id (fn [_])) new-status)))

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

(defn- start-update-handler
  []
  (let [updates (chan)
        socket (zmq/socket context :sub)]
    (zmq/bind (zmq/subscribe socket "") "tcp://*:7720")
    (psi #(zmq/receive socket) #(update! (nippy/thaw %)))))

(defn initialize
  []
  (broker/start frontend-address "tcp://*:7740")
  (start-update-handler))

(defn task-available?
  [method]
  (broker/service-available? method))

(defn status
  [id]
  (status/retrieve id))

(defn- process
  "Sends the message and returns a promise to results"
  [msg]
  (let [{:keys [method id]} msg
        socket (q/create-connected-socket context :req frontend-address id)]
    (q/send-frame socket method (nippy/freeze msg))
    (cons id (q/receive socket 2 zmq/bytes-type))))

(defn publish-task
  [method payload callback]
  (let [id  (crypto.random/url-part 6)
        msg {:id id :body payload :method method}
        work (chan)]
    (log/debug (format "Publishing task to %s" method))
    (swap! callbacks assoc id callback)
    (status/insert! id {:id id
                        :method method
                        :status "pending"
                        :created (java.util.Date.)})
    (go (>! work (process msg)))
    (go (let [[id status result] (<! work)]
          (if (q/status-ok? status)
            (save-results! (nippy/thaw result))
            (status/update! id {:status "failed" :cause (String. result)}))
          ((@callbacks id) (status/retrieve id))
          (cleanup id)))
    (status id)))
