(ns clinicico.worker.task
  (:require [taoensso.nippy :as nippy]
            [zeromq.zmq :as zmq]
            [clinicico.common.zeromq :as q]
            [clinicico.worker.consumer :as consumer]
            [clinicico.worker.config :refer [config]]
            [crypto.random :as crypto]
            [clojure.string :as s :only [blank?]]
            [clojure.tools.logging :as log]))

(defn- updater
  [id socket]
  (fn [content]
    (q/send! socket [(nippy/freeze {:content content :id id})])))

(defn- task-handler
  [task-fn method]
  (fn
    [task]
    (let [context (zmq/context)
          updates-socket (zmq/connect (zmq/socket context :pub) (:updates-socket config))
          id (:id task)
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
             :cause (.getMessage e)}))
        (finally (do (.close updates-socket) (.term context)))))))

(defn initialize
  [method n task-fn]
  (dotimes [n n]
    (consumer/start method (task-handler task-fn method))
    (log/info (format "[main] started worker for %s" method))))
