(ns clinicico.server.router
  (:import [org.zeromq ZMQ])
  (:require [zeromq.zmq :as zmq]
            [clojure.tools.logging :as log]))

(defrecord Router [frontend-address backend-address]
  Runnable
  (run [this]
    (let [ctx (zmq/context)
          frontend (zmq/socket ctx :router)
          backend (zmq/socket ctx :router)
          available-workers (atom 0)
          worker-queue (atom clojure.lang.PersistentQueue/EMPTY)
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
            (let [client-addr (zmq/receive-str frontend)
                  _ (zmq/receive frontend)
                  request (zmq/receive frontend)
                  worker-addr (peek @worker-queue)]
              (log/debug "[router] dispatching from" client-addr "to" worker-addr)
              (swap! worker-queue pop)
              (zmq/send backend (.getBytes worker-addr) ZMQ/SNDMORE)
              (zmq/send backend (byte-array 0) ZMQ/SNDMORE)
              (zmq/send backend (.getBytes client-addr) ZMQ/SNDMORE)
              (zmq/send backend (byte-array 0) ZMQ/SNDMORE)
              (zmq/send backend request))))
        (if (zmq/check-poller items 1 :pollin)
          (do
            (log/debug "[router] backend poll")
            (let [worker-addr (zmq/receive-str backend)]
              (swap! worker-queue #(conj % worker-addr))
              (let [_ (zmq/receive backend) client-addr (zmq/receive-str backend)]
                (log/debug "[router] message from" worker-addr "client-addr" client-addr)
                (if (not= "READY" client-addr)
                  (let [_ (zmq/receive backend) reply (zmq/receive backend)]
                    (zmq/send frontend (.getBytes client-addr) ZMQ/SNDMORE)
                    (zmq/send frontend (byte-array 0) ZMQ/SNDMORE)
                    (zmq/send frontend reply))))))))
      (.close frontend)
      (.close backend)
      (.term ctx))))
