(ns clinicico.server.handlers
  (:require [clojure.tools.logging :as log]
            [clojure.string :only [replace split] :as s]
            [clojure.core.async :as async :refer :all]
            [clj-wamp.server :as wamp]
            [ring.util.response :as resp]
            [org.httpkit.server :as http-kit]
            [clinicico.server.store :as store]
            [clinicico.common.util :refer [dissoc-in]]
            [clinicico.server.service :only [publish-task status task-available?] :as service]))

(def base "http://myapp/")
(def service-rpc-uri (str base "rpc#"))
(def service-status-uri (str base "status#"))

(defn service-run-rpc [method data]
  (let [{:keys [updates status]} (service/publish method data)]
    (go (loop [update (<!! updates)]
          (if (contains? #("completed" "failed" "canceled") updates)
            update ;; Last on probably is the result
            (do
              (log/debug update)
              (wamp/emit-event! service-status-uri update [wamp/*call-sess-id*])
              (recur (<!! update))))))))

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

(defn get-file [id file]
  (let [record (store/get-file id file)]
    (if (nil? record)
      (resp/not-found nil)
      (-> (resp/response (.getInputStream record))
          (resp/content-type (:content-type (.getContentType record)))
          (resp/header "Content-Length" (.getLength record))))))
