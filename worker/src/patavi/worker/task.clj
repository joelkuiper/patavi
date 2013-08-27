(ns patavi.worker.task
  (:require [taoensso.nippy :as nippy]
            [zeromq.zmq :as zmq]
            [patavi.common.zeromq :as q]
            [patavi.worker.consumer :as consumer]
            [patavi.worker.config :refer [config]]
            [crypto.random :as crypto]
            [clojure.string :as s :only [blank? replace]]
            [clojure.tools.logging :as log]))

(def ^:const processing "processing")
(def ^:const accepted "accepted")
(def ^:const failed "failed")

(defn- updater
  [id socket]
  (fn [content]
    (let [base-update {:status processing}]
      (zmq/send socket (.getBytes id) zmq/send-more)
      (zmq/send socket (nippy/freeze (merge base-update (or content {})))))))

(defn- task-handler
  [updates-socket method task-fn]
  (fn [task]
    (let [id (:id task)
          update! (updater id updates-socket)]
      (try
        (do
          (log/info (format "[handler] recieved task %s" id))
          (update! {:status accepted})
          (task-fn method task #(update! {:progress %})))
        (catch Exception e
          (do
            (log/warn e)
            {:status failed
             :cause (.getMessage e)}))))))

(defn initialize
  [method n task-fn]
  (let [context (zmq/context n)
        updates-socket (zmq/bind (zmq/socket context :pub) (:updates-socket config))]
    (dotimes [n n]
      (consumer/start method (task-handler updates-socket method task-fn))
      (log/info (format "[main] started worker for %s" method)))))
