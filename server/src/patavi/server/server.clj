(ns patavi.server.server
  (:gen-class)
  (:require [clojure.tools.logging :as log]
            [clojure.tools.cli :refer [cli]]
            [ring.middleware.reload :as reload]
            [org.httpkit.server :refer :all]
            [patavi.server.http :as http]
            [compojure.handler :refer [api site]]
            [compojure.core :refer [context ANY GET OPTIONS routes defroutes]]
            [patavi.server.handlers :as handlers]
            [patavi.server.middleware :refer :all]
            [patavi.server.service :as service :only [initialize]]))

(declare in-dev?)

(defn assemble-routes []
  (->
    (routes
      (GET "/ws" [:as req] (handlers/handle-service req))
      (OPTIONS "/ws" [] (http/options #{:options :get})))))

(def app
  (->
   (assemble-routes)
   (api)
   (wrap-request-logger)
   (wrap-exception-handler)
   (wrap-response-logger)))

(defn -main
  [& args]
  (let [[options args banner]
        (cli args
             ["-h" "--help" "Show Help" :default false :flag true]
             ["-p" "--port" "Port to listen to" :default 3000 :parse-fn #(Integer. %)]
             ["-d" "--development" "Run server in development mode" :default false :flag true])]
    (defonce in-dev? (:development options))
    (when (:help options)
      (println banner)
      (System/exit 0))
    (let [handler (if in-dev? (reload/wrap-reload app) app)]
      (log/info "running server on:" (:port options))
      (service/initialize)
      (run-server handler {:port (:port options) :thread 256}))))
