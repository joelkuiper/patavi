(ns clinicico.server.tasks
  (:gen-class)
  (:require [clojure.tools.logging :as log]
            [clj-time.core :as time]
            [clinicico.common.zeromq :as q]
            [zeromq.zmq :as zmq]
            [crypto.random :as crypto]
            [clinicico.server.router :as router :refer :all]
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
    (log/debug "[tasks] done with task " task-id)
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


(defn- start-update-handler
  []
  (.start
   (Thread.
    (fn []
      (let [items (zmq/poller context)]
        (zmq/register items updates-socket :pollin) ;; item 0
        (while (not (.. Thread currentThread isInterrupted))
          (zmq/poll items)
          (when (.pollin items 0) ;; process updates
            (update-handler
             (nippy/thaw (zmq/receive updates-socket))))))))))

(defn- start-router
  []
  (router/start frontend-address "tcp://*:7740"))

(defn initialize
  []
  (start-router)
  (start-update-handler))

(defn task-available?
  [method]
  true)

(defn status
  [id]
  (status/retrieve id))

(defn publish-task
  [method payload callback]
  (let [id  (crypto.random/url-part 5)
        msg {:id id :body payload :method method :type "task"}
        socket (q/create-connected-socket context :req frontend-address id)]
    (log/debug (format "Publishing task to %s" method))
    (swap! callbacks assoc id callback)
    (q/send-frame socket method (nippy/freeze msg))
    (status/insert! id {:id id
                        :method method
                        :status "pending"
                        :created (java.util.Date.)})
    (.start (Thread.
              #(let [[status result] (q/receive socket 2 zmq/bytes-type)]
                 (if (q/status-ok? status)
                   (save-results! (nippy/thaw result))
                   (status/update! id {:status "failed" :cause (String. result)}))
                 ((@callbacks id) (status/retrieve id))
                 (cleanup id)
                 (.close socket))))
    (status id)))
