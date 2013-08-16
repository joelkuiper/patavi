(ns clinicico.server.router
  (:require [zeromq.zmq :as zmq]
            [clinicico.common.zeromq :as q]
            [clojure.tools.logging :as log]))

(def ^:private workers (atom {}))
(def ^:private context (zmq/context 2))
(def ^:private max-ttl 5000) ;; in msec

(defrecord Worker [address ttl])

(defn bind-socket
  [context type address]
  (zmq/bind (zmq/socket context type) address))

(defn put-worker [method address]
  (let [worker (Worker. address max-ttl)
        queue (get-in @workers [method :queue] clojure.lang.PersistentQueue/EMPTY)]
    (swap! workers update-in [method :pool] conj worker)
    (swap! workers assoc-in [method :queue] (conj queue worker))))

(defn get-worker [method]
  (let [queue (get-in @workers [method :queue])
        worker (peek queue)]
    (swap! workers assoc-in [method :queue] (pop queue))
    worker))

(defn create-router-fn [frontend-address backend-address]
  (let [[frontend backend :as sides] (map (partial bind-socket context :router) [frontend-address backend-address])
        poller (zmq/poller context 2)]
    (doseq [side sides] (zmq/register poller side :pollin))
    (fn []
      (while (not (.. Thread currentThread isInterrupted))
      (zmq/poll poller)
      (if (zmq/check-poller poller 0 :pollin)
        (let [[client-addr worker-method request] (q/receive frontend [String String zmq/bytes-type])
              worker-addr (get (get-worker worker-method) :address)]
          (if (not (nil? worker-addr))
            (do
              (log/debug "[router] dispatching" worker-method "from" client-addr "to" worker-addr)
              (q/send-frame backend worker-addr q/MSG-REQ client-addr request))
            (do
              (log/debug "[router] no workers for" worker-method)
              (q/send-frame frontend client-addr q/STATUS-ERROR "No workers available")))))
      (if (zmq/check-poller poller 1 :pollin)
        (let [[worker-addr msg-type worker-method] (q/receive backend [String Byte String])]
          (condp = msg-type
            q/MSG-PING  (do (log/debug "[router] PING from" worker-addr)
                            (q/send-frame backend worker-addr q/MSG-PONG))
            q/MSG-READY (do (log/debug "[router] READY from" worker-addr "for" worker-method)
                            (put-worker worker-method worker-addr))
            q/MSG-REP   (let [[client-addr reply] (q/receive-more backend [String zmq/bytes-type])]
                          (log/debug "[router] REPLY from" worker-addr "for" worker-method "client-addr" client-addr)
                          (q/send-frame frontend client-addr q/STATUS-OK reply)))))))))

(defn start
  [frontend-address backend-address]
  (let [router-fn (create-router-fn frontend-address backend-address)]
    (.start (Thread. router-fn))))
