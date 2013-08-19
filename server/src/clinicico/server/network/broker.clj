(ns clinicico.server.network.broker
  (:import clojure.lang.PersistentQueue)
  (:use [clojure.set :only [select]])
  (:require [zeromq.zmq :as zmq]
            [overtone.at-at :as at]
            [clinicico.common.zeromq :as q]
            [clinicico.common.util :refer [now update-vals]]
            [clojure.tools.logging :as log]))

(def ^:private worker-pool (ref {}))
(def ^:private context (zmq/context 2))
(def ^:private max-ttl 3000) ;; in millis
(def ^:private ttl-pool (at/mk-pool))

(defn- check-workers
  [pool]
  (doall
   (map
    (fn [[method workers]]
      (dosync
       (let [curr (:expiry workers)
             expired (select-keys curr (for [[addr ttl] curr :when (> (- (now) ttl) max-ttl)] addr))
             expired? (fn [addr] (some #(= addr %) (keys expired)))
             waiting (get-in @pool [method :waiting])]
         (when (seq expired) (log/warn "workers were expired:" (keys expired)))
         (alter pool assoc-in [method :expiry] (apply (partial dissoc curr) (keys expired)))
         (alter pool assoc-in [method :waiting]
                (into PersistentQueue/EMPTY
                      (select (comp not expired?)
                              (set (take (count waiting) waiting))))))))
    @pool)))

;; Periodically check the workers
(at/every 1000 #(check-workers worker-pool) ttl-pool)

(defn- bind-socket
  [context type address]
  (zmq/bind (zmq/socket context type) address))

(defn- put-worker
  [pool method address]
  (dosync
   (let [requests (get-in @pool [method :requests] PersistentQueue/EMPTY)
         queue (get-in @pool [method :waiting] PersistentQueue/EMPTY)]
     (alter pool assoc-in [method :requests] requests)
     (alter pool assoc-in [method :expiry address] (now))
     (alter pool assoc-in [method :waiting] (conj queue address)))))

(defn- pop-atom
  [key]
  (fn [pool method]
    (dosync
     (let [queue (get-in @pool [method key])
           worker (peek queue)]
       (alter pool assoc-in [method key] (pop queue))
       worker))))

(def pop-worker (pop-atom :waiting))
(def pop-request (pop-atom :requests))

(defn service-available?
  [service]
  (> (count (get-in @worker-pool [service :expiry])) 0))

(defn- update-ttl
  [pool method address expiry]
  (dosync
   (alter pool assoc-in [method :expiry address] expiry)))

(defn- dispatch
  [socket [_ method request :as msg]]
  (dosync
   (when-not (nil? request) (alter worker-pool update-in [method :requests] #(conj % msg)))
   (while (and (not (empty? (get-in @worker-pool [method :waiting])))
               (not (empty? (get-in @worker-pool [method :requests]))))
     (let [[client-addr method request] (pop-request worker-pool method)
           worker-addr (pop-worker worker-pool method)]
       (log/debug "[broker] dispatching!" client-addr "to" (format "%s:%s" method worker-addr))
       (q/send-frame socket worker-addr q/MSG-REQ client-addr request)))))

(defn router-fn
  [frontend-address backend-address]
  (let [[frontend backend :as sides]
        (map (partial bind-socket context :router) [frontend-address backend-address])
        poller (zmq/poller context 2)]
    (fn []
      (doseq [side sides] (zmq/register poller side :pollin))
      (while (not (.. Thread currentThread isInterrupted))
        (zmq/poll poller)
        (if (zmq/check-poller poller 0 :pollin)
          (dispatch backend (q/receive frontend [String String zmq/bytes-type])))
        (if (zmq/check-poller poller 1 :pollin)
          (let [[worker-addr msg-type method] (q/receive backend [String Byte String])]
            (condp = msg-type
              q/MSG-PING  (do (update-ttl worker-pool method worker-addr (now))
                              (q/send-frame backend worker-addr q/MSG-PONG))
              q/MSG-READY (do (log/debug "[router] READY from" worker-addr "for" method)
                              (put-worker worker-pool method worker-addr)
                              (dispatch backend [worker-addr method]))
              q/MSG-REP   (let [[client-addr reply] (q/receive-more backend [String zmq/bytes-type])]
                            (q/send-frame frontend client-addr q/STATUS-OK reply)))))))))

(defn start
  [frontend-address backend-address]
  (let [router-fn (router-fn frontend-address backend-address)
        router (agent 0)]
    (send-off router (fn [_] (router-fn)))))
