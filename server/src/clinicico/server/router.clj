(ns clinicico.server.router
  (:require [zeromq.zmq :as zmq]
            [clinicico.common.zeromq :as q]
            [clojure.tools.logging :as log]))

(defn put-worker [queues method worker]
  (let [queue (get @queues method clojure.lang.PersistentQueue/EMPTY)]
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
          workers (atom {})
          items (zmq/poller ctx 2)]
      (log/debug "Starting router on" frontend-address "and" backend-address)
      (zmq/bind frontend frontend-address)
      (zmq/bind backend backend-address)
      (zmq/register items frontend :pollin)
      (zmq/register items backend :pollin)
      (while (not (.. Thread currentThread isInterrupted))
        (zmq/poll items)
        (if (zmq/check-poller items 0 :pollin)
          (do
            (let [[client-addr worker-method request] (q/receive frontend [String String zmq/bytes-type])
                  worker-addr (get-worker workers worker-method)]
              (if (not (nil? worker-addr))
                (do
                  (log/debug "[router] dispatching" worker-method "from" client-addr "to" worker-addr)
                  (q/send-frame backend worker-addr q/MSG-REQ client-addr request))
                (do
                  (log/debug "[router] no workers for" worker-method)
                  (q/send-frame frontend client-addr q/STATUS-ERROR "No workers available"))))))
        (if (zmq/check-poller items 1 :pollin)
          (do
            (let [[worker-addr msg-type worker-method] (q/receive backend [String Byte String])]
              (condp = msg-type
                q/MSG-PING  (do (log/debug "[router] PING from" worker-addr)
                                (q/send-frame backend worker-addr q/MSG-PONG))
                q/MSG-READY (do (log/debug "[router] READY from" worker-addr "for" worker-method)
                                (put-worker workers worker-method worker-addr))
                q/MSG-REP   (let [[client-addr reply] (q/receive-more backend [String zmq/bytes-type])]
                              (log/debug "[router] REPLY from" worker-addr "for" worker-method "client-addr" client-addr)
                              (q/send-frame frontend client-addr q/STATUS-OK reply)))))))
      (.close frontend)
      (.close backend)
      (.term ctx))))
