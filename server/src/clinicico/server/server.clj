(ns clinicico.server.server
  (:gen-class)
  (:require [clojure.tools.logging :as log]
            [clojure.tools.nrepl.server :only (start-server stop-server) :as repl]
            [clojure.tools.cli :refer [cli]]
            [ring.middleware.reload :as reload]
            [org.httpkit.server :refer :all]
            [clinicico.server.http :as http]
            [compojure.handler :refer [api site]]
            [compojure.core :refer [context ANY GET OPTIONS routes defroutes]]
            [clinicico.server.domain :as domain]
            [clinicico.server.middleware :refer :all]
            [clinicico.server.tasks :as tasks :only [initialize]]))

(declare in-dev?)

(defn assemble-routes []
  (->
    (routes
      (GET "/service/:method/ws" [:as req] (domain/handle-tasks req))
      (OPTIONS "/service/:method/ws" [] (http/options #{:options :get})))))

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
             ["-r" "--repl" "nREPL port to listen to if in production" :default 7888 :parse-fn #(Integer. %)]
             ["-d" "--development" "Run server in development mode" :default false :flag true])]
    (defonce in-dev? (:development options))
    (when (:help options)
      (println banner)
      (System/exit 0))
    (let [handler (if in-dev? (reload/wrap-reload app) app)]
      (log/info "Running server on :" (:port options) "and nREPL running on :" (:repl options))
      (if (not in-dev?) (defonce repl-server (repl/start-server :port (:repl options))))
      (tasks/initialize)
      (run-server handler {:port (:port options)}))))
