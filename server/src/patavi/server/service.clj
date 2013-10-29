(ns patavi.server.service
  (:require [clojure.tools.logging :as log]
            [clojure.core.async :refer [go >! <! filter< chan close! mult tap untap]]
            [clojure.string :as s :only [split]]
            [crypto.random :as crypto]
            [taoensso.carmine :as car :refer (wcar)]
            [taoensso.carmine.message-queue :as car-mq]
            [patavi.common.util :refer [now]]
            [patavi.server.config :refer [config]]))

(def ^:private broker {:pool {} :spec {}})
(defmacro wcar* [& body] `(car/wcar broker ~@body))

(def ^:private river (chan))
(def ^:private flow (mult river))

(defn- update-handler
  [[_ _ chan-name msg]]
  (if-not (nil? msg)
    (do
      (log/debug "[server] got update" chan-name msg)
      (go (>! river msg)))))

(defn initialize
  []
  (car/with-new-pubsub-listener broker
    {"updates-*" update-handler}
    (car/psubscribe "updates-*")))

(defn available?
  [service]
  true)

(def resolved-states [:success :error])

(defn- process
  [updates]
  (let [results (promise)
        resolved? (fn [[_ {:keys [status]}]] (some #{status} resolved-states))
        resolved (filter< resolved? updates)]
    (go (deliver results (<! resolved))
        (close! updates))
    results))

(defn -publish
  [service payload]
  (let [msg-id (crypto.random/url-part 6)
        payload {:body payload :service service :id msg-id}
        updates (filter< (fn [{:keys [id]}] (= id msg-id)) (tap flow (chan)))]
    (wcar* (car-mq/enqueue service payload))
    (log/debug "[server] sending" service  payload)
    {:id msg-id :updates updates :results (process updates)}))

(defn publish
  [service payload]
  (log/debug "[server] sending" service  payload)
  {:id "foo" :updates (chan) :results (promise)})
