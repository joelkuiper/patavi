(ns clinicico.server.router
  (:require [zeromq.zmq :as zmq]
            [clinicico.common.zeromq :as q]
            [clojure.tools.logging :as log]))

(defn put-worker [queues method worker]
  (let [queue (if (contains? @queues method)
                (get @queues method)
                clojure.lang.PersistentQueue/EMPTY)]
    (swap! queues assoc method (conj queue worker))))

(defn get-worker [queues method]
  (let [queue (get @queues method)
        worker (peek queue)]
    (swap! queues assoc method (pop queue))
    worker))


(defrecord Router [frontend-address backend-address]
  Runnable
  (run [this]
    (let [ctx (zmq/context)
          frontend (zmq/socket ctx :router)
          backend (zmq/socket ctx :router)
          worker-queues (atom {})
          items (zmq/poller ctx 2)]
      (log/debug "Starting router on" frontend-address "and" backend-address)
      (zmq/bind frontend frontend-address)
      (zmq/bind backend backend-address)
      (while (not (.. Thread currentThread isInterrupted))
        (zmq/register items frontend :pollin)
        (zmq/register items backend :pollin)
        (zmq/poll items)
        (log/debug "[router] polling!")
        (if (zmq/check-poller items 0 :pollin)
          (do
            (log/debug "[router] frontend poll")
            (let [[client-addr worker-method request] (q/take frontend [String String zmq/bytes-type])
                  worker-addr (get-worker worker-queues worker-method)]
              (if (not (nil? worker-addr))
                (do
                  (log/debug "[router] dispatching" worker-method "from" client-addr "to" worker-addr)
                  (q/send-frame backend worker-addr client-addr request))
                (do
                  (log/debug "[router] no workers for" worker-method)
                  (q/send-frame frontend client-addr q/STATUS-ERROR "No workers available"))))))
        (if (zmq/check-poller items 1 :pollin)
          (do
            (log/debug "[router] backend poll")
            (let [[worker-addr worker-method] (q/take backend [String String])]
              (put-worker worker-queues worker-method worker-addr)
              (let [[client-addr] (q/take-more backend [String])]
                (log/debug "[router] message from" worker-addr "for" worker-method "client-addr" client-addr)
                (if (not= "READY" client-addr)
                  (let [[reply] (q/take-more backend [zmq/bytes-type])]
                    (q/send-frame frontend client-addr q/STATUS-OK reply))))))))
      (.close frontend)
      (.close backend)
      (.term ctx))))
