(ns clinicico.worker.task
  (:require [taoensso.nippy :as nippy]
            [zeromq.zmq :as zmq]
            [clinicico.common.zeromq :as q]
            [clinicico.worker.consumer :as consumer]
            [clinicico.worker.config :refer [config]]
            [crypto.random :as crypto]
            [clojure.string :as s :only [blank? replace]]
            [clojure.tools.logging :as log]))


(defn- updater
  [id socket]
  (fn [content]
    (zmq/send socket (.getBytes id) zmq/send-more)
    (zmq/send socket (nippy/freeze (or content {})))))

(defn- task-handler
  [updates-socket method task-fn]
  (fn [task]
    (let [id (:id task)
          update! (updater id updates-socket)]
      (try
        (do
          (log/debug (format "Recieved task %s" id))
          (update! {:status "processing" :accepted (java.util.Date.)})
          (task-fn method id task #(update! {:progress %})))
        (catch Exception e
          (do
            (log/warn e)
            {:id id
             :status "failed"
             :cause (.getMessage e)}))))))

(defn initialize
  [method n task-fn]
  (let [context (zmq/context n)
        updates-socket (zmq/bind (zmq/socket context :pub) (:updates-socket config))]
    (dotimes [n n]
      (consumer/start method (task-handler updates-socket method task-fn))
      (log/info (format "[main] started worker for %s" method)))))
