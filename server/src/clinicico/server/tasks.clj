(ns clinicico.server.tasks
  (:gen-class)
  (:require [clojure.tools.logging :as log]
            [clj-time.core :as time]
            [zeromq.zmq :as zmq]
            [taoensso.nippy :as nippy]))

(def ^{:private true} statuses (atom {}))
(def ^{:private true} callbacks (atom {}))

(defonce context (zmq/context 2))

(def ventilator-socket
  (zmq/bind (zmq/socket context :push) "tcp://*:7710"))

(def updates-socket
  (zmq/subscribe (zmq/bind (zmq/socket context :sub) "tcp://*:7720") ""))

(defn- cleanup
  [task-id]
  (do
    (log/debug "Done with task " task-id (@statuses task-id))
    (swap! callbacks dissoc task-id)))

(defn- task-update
  [update]
  (let [id (:id update)
        content (into {} (filter
                           (comp not nil? val) (:content update)))
        old-status (or (@statuses id) {})
        callback (or (@callbacks id) (fn [_]))]
    (swap! statuses assoc id (merge old-status content))
    (callback (@statuses id))
    (when (contains? #{"failed" "canceled" "completed"} (:status content))
      (cleanup id))))


(defn- update-handler
  [update]
  (when (= (:type update) "task") (task-update update)))

;; FIXME: see https://github.com/ptaoussanis/nippy/issues/23
(defn initialize
  []
  (.start
    (Thread.
      (fn []
        (while (not (.. Thread currentThread isInterrupted))
          (let [msg (zmq/receive updates-socket)]
            (try
              (update-handler (nippy/thaw msg {:read-eval? true}))
              (catch Exception e (log/warn "failed to process message" (.getMessage e))))))))))

(defn task-available?
  [method]
  true)

(defn status
  [id]
  (@statuses id))

(defn publish-task
  [method payload callback]
    (let [id (str (java.util.UUID/randomUUID))
          msg {:id id :body payload :method method :type "task"}]
      (log/debug (format "Publishing task to %s" method))
      (zmq/send ventilator-socket (nippy/freeze msg))
      (swap! statuses assoc id {:id id :method method :status "pending" :created (time/now)})
      (swap! callbacks assoc id callback)
      (status id)))
