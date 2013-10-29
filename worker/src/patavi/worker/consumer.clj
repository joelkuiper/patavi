(ns patavi.worker.consumer
  (:require [taoensso.nippy :as nippy]
            [clojure.core.async :refer [thread >!! <!! close! chan]]
            [patavi.common.util :refer [insert]]
            [patavi.worker.config :refer [config]]
            [taoensso.carmine :as car :refer (wcar)]
            [taoensso.carmine.message-queue :as car-mq]
            [clojure.tools.logging :as log]))

(def ^:private broker {:pool {} :spec {}})
(defmacro wcar* [& body] `(car/wcar broker ~@body))

(defn- wrap-exception
  [fn & params]
  (try
    (apply fn params)
    (catch Exception e
      (do (log/error e)
          {:status :error
           :cause (.getMessage e)}))))

(defn- send-update!
  [id content]
     (wcar* (car/publish (str "updates-" id) {:status :update :content content})))

(defn- handle-message
  [handler]
  (fn
    [{:keys [message attempt]}]
    (let [work (chan)
          {:keys [id body] :as content} message
          updater (partial send-update! id)
          result (wrap-exception handler body updater)
          ]
       (wcar* (car/publish (str "updates-" id) (<!! work)))
       result)))


(defn start
  [service handler]
  (car-mq/worker broker service
                 {:throttle-ms 10
                  :handler (handle-message handler)}))
