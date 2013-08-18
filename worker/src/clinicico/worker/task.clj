(ns clinicico.worker.task
  (:require [taoensso.nippy :as nippy]
            [zeromq.zmq :as zmq]
            [clinicico.common.zeromq :as q]
            [clinicico.worker.consumer :as consumer]
            [crypto.random :as crypto]
            [clojure.string :as s :only [blank?]]
            [clojure.tools.logging :as log]))

(defonce context (zmq/context))
(def updates (zmq/connect (zmq/socket context :pub) "tcp://localhost:7720"))

(defn update!
  ([id content]
   (let [update {:content content :id id}]
     (zmq/send updates (nippy/freeze update)))))

(defn- task-handler
  [task-fn method]
  (fn
    [task]
    (let [id (:id task)]
      (try
        (do
          (log/debug (format "Recieved task %s" id))
          (update! id {:status "processing" :accepted (java.util.Date.)})
          (let [results (task-fn method id (:body task) #(update! id {:progress %}))]
            (update! id {:progress "done"
                         :completed (java.util.Date.)})
            results))
        (catch Exception e
          (update! id {:status "failed"
                       :progress "none"
                       :cause (.getMessage e)})
          nil)))))

(defn initialize
  [method n task-fn]
  (dotimes [n n]
    (consumer/start method (task-handler task-fn method))
    (log/info (format "[main] started worker for %s" method))))
