(ns clinicico.server.router
  (:import clojure.lang.PersistentQueue)
  (:use [clojure.set :only [select]])
  (:require [zeromq.zmq :as zmq]
            [overtone.at-at :as at]
            [clinicico.common.zeromq :as q]
            [clojure.tools.logging :as log]))

(def ^:private worker-pool (ref {}))
(def ^:private context (zmq/context 2))
(def ^:private max-ttl 5000) ;; in millis
(def ^:private ttl-pool (at/mk-pool))

(defn update-vals [map vals f]
  (reduce #(update-in % [%2] f) map vals))

(defn- now
  []
  (System/currentTimeMillis))

(defn- check-workers
  [pool]
  (doall
   (map
    (fn [[method workers]]
      (dosync
       (let [curr (:ttl workers)
             expired (select-keys curr (for [[addr ttl] curr :when (> (- (now) ttl) max-ttl)] addr))
             expired? (fn [addr] (some #(= addr %) (keys expired)))
             queue (get-in @pool [method :queue])]
         (when-not (empty? expired) (log/warn (count expired) "workers were expired:" (keys expired)))
         (alter pool assoc-in [method :ttl] (apply (partial dissoc curr) (keys expired)))
         (alter pool assoc-in [method :queue]
                (into PersistentQueue/EMPTY
                      (select (comp not expired?)
                              (into #{} (take (count queue) queue))))))))
    @pool)))

;; Periodically check the workers
(at/every 1000 #(check-workers worker-pool) ttl-pool)

(defn bind-socket
  [context type address]
  (zmq/bind (zmq/socket context type) address))

(defn put-worker [pool method address]
  (dosync
   (let [queue (get-in @pool [method :queue] PersistentQueue/EMPTY)]
     (alter pool assoc-in [method :ttl address] (now))
     (alter pool assoc-in [method :queue] (conj queue address)))))

(defn pop-worker [pool method]
  (dosync
   (let [queue (get-in @pool [method :queue])
         worker (peek queue)]
     (alter pool assoc-in [method :queue] (pop queue))
     worker)))

(defn- update-ttl
  [pool method address ttl]
  (dosync
   (alter pool assoc-in [method :ttl address] ttl)))

(defn create-router-fn [frontend-address backend-address]
  (let [[frontend backend :as sides]
          (map (partial bind-socket context :router) [frontend-address backend-address])
        poller (zmq/poller context 2)]
    (doseq [side sides] (zmq/register poller side :pollin))
    (fn []
      (while (not (.. Thread currentThread isInterrupted))
        (zmq/poll poller)
        (if (zmq/check-poller poller 0 :pollin)
          (let [[client-addr method request] (q/receive frontend [String String zmq/bytes-type])
                worker-addr (pop-worker worker-pool method)]
            (if (not (nil? worker-addr))
              (do
                (log/debug "[router] dispatching" method "from" client-addr "to" worker-addr)
                (q/send-frame backend worker-addr q/MSG-REQ client-addr request))
              (do
                (log/debug "[router] no workers for" method)
                (q/send-frame frontend client-addr q/STATUS-ERROR "No workers available")))))
        (if (zmq/check-poller poller 1 :pollin)
          (let [[worker-addr msg-type method] (q/receive backend [String Byte String])]
            (condp = msg-type
              q/MSG-PING  (do (log/debug "[router] PING from" worker-addr)
                              (update-ttl worker-pool method worker-addr (now))
                              (q/send-frame backend worker-addr q/MSG-PONG))
              q/MSG-READY (do (log/debug "[router] READY from" worker-addr "for" method)
                              (put-worker worker-pool method worker-addr))
              q/MSG-REP   (let [[client-addr reply] (q/receive-more backend [String zmq/bytes-type])]
                            (log/debug "[router] REPLY from" worker-addr "for" method "client-addr" client-addr)
                            (q/send-frame frontend client-addr q/STATUS-OK reply)))))))))

(defn start
  [frontend-address backend-address]
  (let [router-fn (create-router-fn frontend-address backend-address)]
    (.start (Thread. router-fn))))
