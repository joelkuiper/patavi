(ns clinicico.server.server
  (:gen-class)
  (:use [org.httpkit.server]
        [clinicico.server.util]
        [clinicico.server.middleware]
        [ring.middleware.jsonp]
        [clojure.tools.cli :only [cli]]
        [compojure.handler :only [api site]]
        [compojure.core :only [context ANY GET OPTIONS routes defroutes]])
  (:require [clojure.tools.logging :as log]
            [clojure.tools.nrepl.server :only (start-server stop-server) :as repl]
            [ring.util.response :as resp]
            [ring.middleware.reload :as reload]
            [cheshire.core :as json]
            [clinicico.server.domain :as domain]
            [clinicico.server.http :as http]
            [clinicico.server.store :as store]
            [clinicico.server.tasks :as tasks :only [initialize]]))

(declare in-dev?)
(def match-uuid #"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
(defn assemble-routes []
  (->
    (routes
      (context "/results" []
               (GET "/:id/:file" [id file]
                    (let [record (store/get-file id file)]
                      (if (nil? record)
                        (resp/not-found nil)
                        (-> (resp/response (.getInputStream record))
                            (resp/content-type (:content-type (.getContentType record)))
                            (resp/header "Content-Length" (.getLength record))))))
               (ANY ["/:id" :id match-uuid] [id] domain/result-resource))
      (context "/tasks" []
               (ANY "/:method" [method] domain/tasks-resource)
               (ANY ["/:method/:id" :id match-uuid] [method id] domain/task-resource)
               (OPTIONS ["/:method/:id/status" :id match-uuid] [] (http/options #{:options :get}))
               (GET ["/:method/:id/status" :id match-uuid] [method id] domain/task-status)))))


(def app
  (->
    (assemble-routes)
    (wrap-json-with-padding)
    (api)
    (ignore-trailing-slash)
    (wrap-cors-request)
    (wrap-request-logger)
    (wrap-exception-handler)
    (wrap-response-logger)))

(defn -main
  [& args]
  (let [[options args banner]
        (cli args
             ["-h" "--help" "Show Help" :default false :flag true]
             ["-p" "--port" "Port to listen to" :default 3000 :parse-fn #(Integer. %)]
             ["-r" "--repl" "nREPL port to listen to" :default 7888 :parse-fn #(Integer. %)]
             ["-d" "--development" "Run server in development mode" :default false :flag true])]
    (defonce in-dev? (:development options))
    (when (:help options)
      (println banner)
      (System/exit 0))
    (let [handler (if in-dev? (reload/wrap-reload app) app)]
      (log/info "Running server on port" (:port options) "; nREPL running on" (:repl options))
      (defonce repl-server (repl/start-server :port (:repl options)))
      (tasks/initialize)
      (run-server handler {:port (:port options)}))))
