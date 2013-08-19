(ns clinicico.server.network.broker
  (:import clojure.lang.PersistentQueue)
  (:use [clojure.set :only [select]])
  (:require [zeromq.zmq :as zmq]
            [overtone.at-at :as at]
            [clinicico.common.zeromq :as q]
            [clinicico.common.util :refer [now update-vals]]
            [clojure.tools.logging :as log]))

(def ^:private services (atom {}))
(def ^:private context (zmq/context 2))
(def ^:private max-ttl 3000) ;; in millis
(def ^:private ttl-pool (at/mk-pool))

(defrecord Service [name waiting workers requests])
(defrecord Worker [address service expiry])

(defn- bind-socket
  [context type address]
  (zmq/bind (zmq/socket context type) address))

(defn- request-service
  [name]
  (let [service (get @services name)]
    (if (nil? service)
      (let [new-service (Service. name PersistentQueue/EMPTY #{} PersistentQueue/EMPTY)]
        (swap! services assoc name new-service)))
    (get @services name)))

(defn- put-worker
  [service-name address]
  (let [service (request-service service-name)
        worker (Worker. address service-name (now))]
    (swap! services assoc service-name
           (-> service
               (update-in [:workers] #(conj % worker))
               (update-in [:waiting] #(conj % worker))))))

(defn- available?
  [key]
  (fn [service]
    (if-let [itm (peek (get service key))]
      (do
        (swap! services assoc-in [(:name service) key]
               (pop (get service key)))
        itm))))

(def ^:private available-worker (available? :waiting))
(def ^:private available-request (available? :requests))

(defn service-available?
  [service-name]
  (> (count (get-in @services [service-name :workers])) 0))

(defn- update-expiry
  [service-name address]
  (let [service (request-service service-name)
        worker (first (filter #(= address (get % :address)) (get service :workers)))]
    (swap! services assoc (:name service)
           (-> service
               (update-in [:workers] #(disj % worker))
               (update-in [:workers] #(conj % (assoc worker :expiry (now))))))))

(defn- purge
  []
  (doall (map
          (fn [service]
            (let [workers (:workers service)
                  expired (filter (fn [{:keys [expiry]}] (> (- (now) expiry) max-ttl)) workers)
                  expired? (fn [worker] (some #(= (:address worker) (:address %)) expired))
                  waiting (:waiting service)]
              (when (seq expired)
                (log/warn "workers were expired:" (map :address expired))
                (swap! services assoc (:name service)
                       (-> service
                           (assoc :workers (apply (partial disj workers) expired))
                           (assoc :waiting (into PersistentQueue/EMPTY
                                                 (select (comp not expired?)
                                                         (set (take (count waiting) waiting))))))))))
          (vals @services))))

;; Periodically check the workers
(at/every 1000 #(try (purge) (catch Exception e (log/warn (.printStackTrace e)))) ttl-pool)

(defn- dispatch
  [socket [_ service-name request :as msg]]
  (let [service (request-service service-name)]
    (when-not (nil? request) (swap! services assoc service-name
                                  (-> service
                                      (update-in [:requests] #(conj % msg)))))
    (while (and (not (empty? (get-in @services [service-name :waiting])))
                (not (empty? (get-in @services [service-name :requests]))))
      (let [service (request-service service-name)
            [client-addr _ request] (available-request service)
            worker-addr (:address (available-worker service))]
        (log/debug "[broker] dispatching!" client-addr "to" (format "%s:%s" service-name worker-addr))
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
              q/MSG-PING  (do (update-expiry method worker-addr)
                              (q/send-frame backend worker-addr q/MSG-PONG))
              q/MSG-READY (do (log/debug "[router] READY from" worker-addr "for" method)
                              (put-worker method worker-addr)
                              (dispatch backend [worker-addr method]))
              q/MSG-REP   (let [[client-addr reply] (q/receive-more backend [String zmq/bytes-type])]
                            (q/send-frame frontend client-addr q/STATUS-OK reply)))))))))

(defn start
  [frontend-address backend-address]
  (let [router-fn (router-fn frontend-address backend-address)
        router (agent 0)]
    (.start (Thread. router-fn))))
