(ns clinicico.worker.task
  (:require [taoensso.nippy :as nippy]
            [zeromq.zmq :as zmq]
            [clojure.string :as s :only [blank?]]
            [clojure.tools.logging :as log]))

(defonce context (zmq/context 3))
(def ventilator "tcp://localhost:7710")
(def updates (zmq/connect (zmq/socket context :pub) "tcp://localhost:7720"))
(def sink (zmq/connect (zmq/socket context :push) "tcp://localhost:7730"))

(defn update!
  ([id content]
   (update! id content "task"))
  ([id content type]
   (let [update {:content content :id id :type type}]
     (zmq/send updates (nippy/freeze update)))))

(defn sink!
  [results]
  (zmq/send sink (nippy/freeze results)))

(defn- task-handler
  [task-fn]
  (fn
    [task]
    (let [method (:method task)
          id (:id task)]
      (try
        (do
          (log/debug (format "Recieved task %s" id))
          (update! id {:status "processing" :accepted (java.util.Date.)})
          (sink! (task-fn method id (:body task) #(update! id {:progress %})))
          (update! id {:status "completed"
                       :completed (java.util.Date.)
                       :result true}))
        (catch Exception e
          (update! id {:status "failed"
                       :progress "none"
                       :cause (.getMessage e)}))))))

(defn- start-consumer
  "Starts a consumer in a separate thread"
  [method handler]
  (let [socket (zmq/connect (zmq/socket context :pull) ventilator)
        thread (Thread.
                 (fn []
                   (while (not (.. Thread currentThread isInterrupted))
                     (let [msg (nippy/thaw (zmq/receive socket))]
                       (when (= (:method msg) method) (handler msg))))))]
    (.start thread)))

(defn initialize
  [method n task-fn]
  (dotimes [n n]
    (let [handler (task-handler task-fn)]
      (start-consumer method handler)
      (log/info (format "[main] Connected worker %d. for %s" (inc n) method)))))
