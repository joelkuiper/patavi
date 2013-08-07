(ns clinicico.server.tasks
  (:gen-class)
  (:require [clojure.tools.logging :as log]
            [clj-time.core :as time]
            [zeromq.zmq :as zmq]
            [clinicico.server.store :as status]
            [taoensso.nippy :as nippy]))

(def ^{:private true} callbacks (atom {}))

(defonce context (zmq/context 3))

(def ventilator-socket
  (zmq/bind (zmq/socket context :push) "tcp://*:7710"))

(def updates-socket
  (zmq/subscribe (zmq/bind (zmq/socket context :sub) "tcp://*:7720") ""))

(def sink-socket
  (zmq/bind (zmq/socket context :pull) "tcp://*:7730"))

(defn- cleanup
  [task-id]
  (do
    (log/debug "Done with task " task-id)
    (swap! callbacks dissoc task-id)))

(defn- task-update
  [update]
  (let [id (:id update)
        content (into {} (filter
                           (comp not nil? val) (:content update)))
        old-status (or (status/retrieve id) {})
        new-status (merge old-status content)]
    (status/update! id new-status)
    ((@callbacks id) new-status)
    (when (contains? #{"failed" "canceled" "completed"} (:status content))
      (cleanup id))))


(defn- update-handler
  [update]
  (when (= (:type update) "task") (task-update update)))

(defn save-results!
  [results]
  (let [id (results :id)
        old-status (status/retrieve id)]
    (status/save-files! id (results :files))
    (status/update! id (merge old-status
                              {:results
                               {:body (results :results)
                                :files (map (fn [f] {:name (get f "name")
                                                     :mime (get f "mime")})
                                            (results :files))}}))))

(defn initialize
  []
  (.start
    (Thread.
      (fn []
        (let [items (zmq/poller context 2)]
          (zmq/register items updates-socket :pollin) ;; item 0
          (zmq/register items sink-socket :pollin) ;; item 1
          (while (not (.. Thread currentThread isInterrupted))
            (zmq/poll items)
            (when (zmq/check-poller items 0 :pollin) ;; process updates
              (update-handler
                (nippy/thaw (zmq/receive updates-socket))))
            (when (zmq/check-poller items 1 :pollin) ;; process results from sink
              (let [results (nippy/thaw (zmq/receive sink-socket))
                    id (:id results)]
                (save-results! results)
                ((@callbacks id) (status/retrieve id))))))))))

(defn task-available?
  [method]
  true)

(defn status
  [id]
  (status/retrieve id))

(defn publish-task
  [method payload callback]
  (let [id (str (java.util.UUID/randomUUID))
        msg {:id id :body payload :method method :type "task"}]
    (log/debug (format "Publishing task to %s" method))
    (swap! callbacks assoc id callback)
    (zmq/send ventilator-socket (nippy/freeze msg))
    (status/insert! id {:id id
                        :method method
                        :status "pending"
                        :created (java.util.Date.)})
    (status id)))
