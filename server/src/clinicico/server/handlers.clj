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

(defn dispatch
  [method data]
  (let [{:keys [updates close results]} (service/publish method data)]
    (go (loop [update (<! updates)]
          (when ((comp not nil?) update)
            (do
              (wamp/emit-event! service-status-uri update [wamp/*call-sess-id*])
              (recur (<! updates))))))
    (try @results
         (catch Exception e
           (do (log/error e)
               (throw e))))))

(defn service-run-rpc [method data]
  (if (service/available? method)
    (dispatch method data)
    {:error {:uri service-rpc-uri
             :message (str "service " method " not avaiable")}}))

(def origin-re #"http://.*")

(defn handle-service
  "Returns a http-kit websocket handler with wamp subprotocol"
  [request]
  (let [method (get-in request [:route-params :method])]
    (wamp/with-channel-validation request channel origin-re
      (wamp/http-kit-handler channel
                             {:on-call {service-rpc-uri (partial service-run-rpc method)}
                              :on-subscribe {service-status-uri true}
                              :on-publish {service-status-uri true
                                           (str service-status-uri "pub-only") true}}))))
