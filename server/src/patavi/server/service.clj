(ns patavi.server.service
  (:require [clojure.tools.logging :as log]
            [clojure.core.async :refer [go >! <! filter< chan close! mult tap untap]]
            [clojure.string :as s :only [replace]]
            [patavi.common.zeromq :as q]
            [clj-time.core :refer [interval in-millis now]]
            [zeromq.zmq :as zmq]
            [crypto.random :as crypto]
            [patavi.server.network.broker :as broker]
            [environ.core :refer [env]]
            [taoensso.nippy :as nippy]))

(def ^:private frontend-address (env :broker-frontend-socket))
(def ^:private updates-address (env :broker-updates-socket))

(def context (zmq/context 2))
(def cached-socket
  (memoize q/create-connected-socket))

(def ^:private updates-stream
  ((fn []
     (let [socket (zmq/subscribe (cached-socket context :sub updates-address) "")
           updates (chan)
           mult (mult updates)]
       (go (loop [msg (zmq/receive-all socket)]
             (let [[id _ content] msg
                   update (nippy/thaw content)]
               (>! updates {:msg update :id (String. id)})
               (recur (zmq/receive-all socket)))))
       {:tap (fn [id]
               (let [u (chan)]
                 (filter< (fn [update] (= id (:id update))) (tap mult u))))}))))

(defn initialize
  []
  (broker/start frontend-address (env :broker-backend-socket) updates-address))

(defn available?
  [service]
  (broker/service-available? service))


(def ^:private etas (atom {}))
(def ^:private default-eta 36000)

(defn ^:private update-eta
  [service duration]
  (swap! etas update-in [service]
         (fn [average]
           (if-not (nil? average)
             (double (/ (+ average duration) 2))
             default-eta))))

(defn eta
  "Returns the estimated time of arrival in msec"
  [service]
  (get @etas service default-eta))

(defn- process
  "Sends the message and returns the results"
  [msg updates]
  (let [start-time (now)
        {:keys [service id]} msg]
    (with-open [socket (cached-socket context :req frontend-address id)]
      (q/send! socket [service (nippy/freeze msg)])
      (try
        (let [[status result] (q/receive! socket 2 zmq/bytes-type)]
          (if (q/status-ok? status)
            (let [results (nippy/thaw result)]
              (update-eta service (in-millis (interval start-time (now))))
              (if (= (:status results) "failed")
                (throw (Exception. (:cause results)))
                results))
            (throw (Exception. (String. result)))))
        (catch Exception e (do (log/error e) (throw e)))
        (finally (close! updates))))))

(defn publish
  [service payload]
  (let [id (crypto.random/url-part 6)
        updates ((:tap updates-stream) id)
        msg {:id id :body payload :service service}]
    (go (>! updates {:status "pending"
                     :created (now)}))
    {:updates updates :id id :results (future (process msg updates))}))
