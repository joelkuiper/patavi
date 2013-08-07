(ns clinicico.server.tasks
  (:gen-class)
  (:use [clinicico.server.router])
  (:require [clojure.tools.logging :as log]
            [clj-time.core :as time]
            [zeromq.zmq :as zmq]
            [clinicico.server.store :as status]
            [taoensso.nippy :as nippy]))

(def ^{:private true} callbacks (atom {}))

(defonce context (zmq/context 3))

(def frontend-address "ipc://frontend.ipc")

(def updates-socket
  (zmq/subscribe (zmq/bind (zmq/socket context :sub) "tcp://*:7720") ""))

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
    ((@callbacks id) new-status)))


(defn- update-handler
  [update]
  (when (= (:type update) "task") (task-update update)))

(defn save-results!
  [results]
  (let [id (results :id)
        old-status (status/retrieve id)
        new-status {:status "completed"
                    :results {:body (results :results)
                              :files (map (fn [f] {:name (get f "name")
                                                   :mime (get f "mime")})
                                          (results :files))}}]
    (status/save-files! id (results :files))
    (status/update! id (merge old-status new-status))))


(defn initialize
  []
  (.start (Thread. (clinicico.server.router.Router. frontend-address "tcp://*:7740")))
  (.start
    (Thread.
      (fn []
        (let [items (zmq/poller context 1)]
          (zmq/register items updates-socket :pollin) ;; item 0
          (while (not (.. Thread currentThread isInterrupted))
            (zmq/poll items)
            (when (zmq/check-poller items 0 :pollin) ;; process updates
              (update-handler
                (nippy/thaw (zmq/receive updates-socket))))))))))

(defn task-available?
  [method]
  true)

(defn status
  [id]
  (status/retrieve id))

(defn publish-task
  [method payload callback]
  (let [id (str (java.util.UUID/randomUUID))
        msg {:id id :body payload :method method :type "task"}
        socket (zmq/socket context :req)]
    (log/debug (format "Publishing task to %s" method))
    (swap! callbacks assoc id callback)
    (zmq/set-identity socket (.getBytes id))
    (zmq/connect socket frontend-address)
    (zmq/send socket (nippy/freeze msg))
    (status/insert! id {:id id
                        :method method
                        :status "pending"
                        :created (java.util.Date.)})
    (.start (Thread.
              #(do
                 (save-results! (nippy/thaw (zmq/receive socket)))
                 ((@callbacks id) (status/retrieve id))
                 (cleanup id)
                 (try (.close socket) (catch Throwable e (log/warn e))))))
    (status id)))
