(ns clinicico.server.server
  (:gen-class)
  (:use [org.httpkit.server]
        [clinicico.server.middleware]
        [ring.middleware.jsonp]
        [clojure.tools.cli :only [cli]]
        [compojure.handler :only [api site]]
        [compojure.core :only [context ANY GET OPTIONS routes defroutes]])
  (:require [clojure.tools.logging :as log]
            [clojure.tools.nrepl.server :only (start-server stop-server) :as repl]
            [ring.util.response :as resp]
            [ring.middleware.reload :as reload]
            [clinicico.server.domain :as domain]
            [clinicico.server.store :as store]
            [clinicico.server.http :as http]
            [clinicico.server.tasks :as tasks :only [initialize]]))

(declare in-dev?)
(defn assemble-routes []
  (->
    (routes
      (context "/tasks" []
               ;; Resources managed by liberator
               (ANY "/:method" [method] domain/tasks-resource)
               (ANY ["/:method/:id"] [method id] domain/task-resource)
               ;; Handle retrieval of file
               (GET "/:method/:id/files/:file" [id file]
                    (let [record (store/get-file id file)]
                      (if (nil? record)
                        (resp/not-found nil)
                        (-> (resp/response (.getInputStream record))
                            (resp/content-type (:content-type (.getContentType record)))
                            (resp/header "Content-Length" (.getLength record))))))
               ;; Handle status routes (for WebSockets / Comet)
               (OPTIONS ["/:method/:id/status"] [] (http/options #{:options :get}))
               (GET ["/:method/:id/status"] [method id] domain/task-status)))))

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
