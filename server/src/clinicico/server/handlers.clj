(ns clinicico.server.handlers
  (:use clinicico.server.util)
  (:require [clojure.tools.logging :as log]
            [clojure.string :only [replace split] :as s]
            [clojure.core.async :as async :refer :all]
            [clj-wamp.server :as wamp]
            [ring.util.response :as resp]
            [org.httpkit.server :as http-kit]
            [clinicico.common.util :refer [dissoc-in]]
            [clinicico.server.service :only [publish available?] :as service]))

(def base "http://myapp/")
(def service-rpc-uri (str base "rpc#"))
(def service-status-uri (str base "status#"))

(defn dispatch-rpc
  [method data]
  (let [listeners [wamp/*call-sess-id*]
        {:keys [updates close results]} (service/publish method data)]
    (try
      (go (loop [update (<! updates)]
            (when ((comp not nil?) update)
              (wamp/emit-event! service-status-uri update listeners)
              (recur (<! updates)))))
      @results
      (catch Exception e
        {:error {:uri service-rpc-uri
                 :message (.getMessage e)}}))))

(defn service-run-rpc [method data]
  (if (service/available? method)
    (dispatch-rpc method data)
    {:error {:uri service-rpc-uri
             :message (str "service " method " not avaiable")}}))

(def origin-re #"http://.*")

(defn handle-service
  "Returns a http-kit websocket handler with wamp subprotocol"
  [request]
  (wamp/with-channel-validation request channel origin-re
    (wamp/http-kit-handler channel
                           {:on-call {service-rpc-uri service-run-rpc}
                            :on-subscribe {service-status-uri true}
                            :on-publish {service-status-uri true}})))
