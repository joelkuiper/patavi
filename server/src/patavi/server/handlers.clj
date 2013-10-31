(ns patavi.server.handlers
  (:use patavi.server.util)
  (:require [clojure.tools.logging :as log]
            [clojure.string :only [replace split] :as s]
            [clojure.core.async :as async :refer [go <! >! chan]]
            [clj-wamp.server :as wamp]
            [ring.util.response :as resp]
            [org.httpkit.server :as http-kit]
            [environ.core :refer [env]]
            [patavi.common.util :refer [dissoc-in]]
            [patavi.server.service :only [publish available? eta] :as service]))

(def base (env :ws-base-uri))
(def service-rpc-uri (str base "rpc#"))
(def service-status-uri (str base "status#"))

(defn dispatch-rpc
  [service data]
  (let [listeners [wamp/*call-sess-id*]
        {:keys [updates close results]} (service/publish service data)]
    (try
      (go (loop [update (<! updates)]
            (when ((comp not nil?) update)
              (wamp/emit-event! service-status-uri (:msg update) listeners)
              (recur (<! updates)))))
      (deref results (env :task-timeout) {:error {:uri service-rpc-uri :message "this took way too long"}})
      (catch Exception e
        (do
          (log/error e)
          {:error {:uri service-rpc-uri
                   :message (.getMessage e)}})))))

(defn service-run-rpc [service data]
  (if (service/available? service)
    (dispatch-rpc service data)
    {:error {:uri service-rpc-uri
             :message (str "service " service " not available")}}))

(def origin-re (re-pattern (env :ws-origin-re)))

(defn handle-service
  "Returns a http-kit websocket handler with wamp subprotocol"
  [request]
  (wamp/with-channel-validation request channel origin-re
    (wamp/http-kit-handler channel
                           {:on-call {service-rpc-uri service-run-rpc}
                            :on-subscribe {service-status-uri true}
                            :on-publish {service-status-uri true}})))
