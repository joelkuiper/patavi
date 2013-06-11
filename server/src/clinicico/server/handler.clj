(ns clinicico.server.handler
  (:gen-class)
  (:use [org.httpkit.server]
        [compojure.core :only [context ANY GET OPTIONS routes defroutes]]
        [compojure.handler :only [api site]]
        [clinicico.server.util]
        [clinicico.server.middleware]
        [ring.middleware.jsonp]
        [clojure.tools.cli :only [cli]]
        [liberator.core :only [resource defresource request-method-in]])
  (:require [clojure.tools.logging :as log]
            [clojure.java.io :only [reader] :as io]
            [clojure.string :only [replace split] :as s]
            [ring.util.response :as resp]
            [ring.middleware.reload :as reload]
            [clojure.tools.nrepl.server :only (start-server stop-server) :as repl]
            [cheshire.core :as json]
            [clinicico.server.resource :as hal]
            [clinicico.server.http :as http]
            [clinicico.server.store :as store]
            [clinicico.server.tasks :only [initialize publish-task
                                           status task-available?] :as tasks]))

(declare in-dev?)

(defn strip-slash
  [url]
  (str url (when-not (.endsWith url "/") "/") "status"))

(defn represent-task
  [task url]
  (let [status-url (strip-slash url)
        resource
        (-> (hal/new-resource url)
            (hal/add-link :href status-url
                          :rel "status"
                          :websocket (s/replace status-url #"http(s)?" "ws")
                          :comment "XHR long-polling and WebSocket for status updates")
            (hal/add-properties task))]
    (if (contains? task :results)
      (hal/add-link resource
                    :href (str (http/url-base) "/results/" (:id task))
                    :rel "results")
      resource)))

(defn handle-new-task
  [ctx]
  (let [task (:task ctx)
        url (http/url-from (:request ctx) (:id task))
        resource (represent-task task url)]
    {:status 202
     :headers {"Location" url
               "Content-Type" "application/json"}
     :body (hal/resource->representation resource :json)}))

(def listeners (atom {}))

(defn broadcast-update
  [task]
  (let [id (:id task)
        method (:method task)
        resource (represent-task task (str (http/url-base) "/tasks/" method "/" id "/"))]
    (doseq [client (get @listeners id)]
      (send! client (hal/resource->representation resource :json)))))

(defn task-status
  [request]
  (with-channel request channel
    (let [id (get-in request [:route-params :id])
          status (tasks/status id)
          current-listeners (get @listeners id #{})]
      (if (nil? status)
        (send! channel (resp/status (resp/response "Task not found") 404))
        (do
          (swap! listeners assoc id (merge current-listeners channel))
          (when (or (contains? #{"failed" "completed"} (:status status))
                    (get-in request [:params :latest]))
            (broadcast-update status))
          (on-close channel (fn [_] (swap! listeners dissoc id))))))))

(defresource tasks-resource
  :available-media-types ["application/json"]
  :available-charsets ["utf-8"]
  :method-allowed? (request-method-in :options :post :get)
  :generate-options-header (fn [_] {"Allow" "OPTIONS, GET, POST"})
  :service-available? (fn [ctx]
                        (tasks/task-available?
                          (get-in ctx [:request :route-params :method])))
  :handle-ok (fn [ctx] (json/encode {:status "Up and running"}))
  :handle-created handle-new-task
  :post! (fn [ctx]
           (let [callback broadcast-update
                 method (get-in ctx [:request :route-params :method])
                 body (get-in ctx [:request :body])
                 payload (when body (slurp body))]
             (assoc ctx :task (tasks/publish-task method payload callback)))))

(defresource task-resource
  :available-media-types ["application/json"]
  :available-charsets ["utf-8"]
  :method-allowed? (request-method-in :options :delete :get)
  :generate-options-header (fn [_] {"Allow" "OPTIONS, GET, DELETE"})
  :exists? (fn [ctx] (not (nil?
                            (tasks/status
                              (get-in ctx [:request :params :id])))))
  :handle-ok (fn [ctx]
               (let [id (get-in ctx [:request :params :id])
                     task (tasks/status id)
                     resource (represent-task task (http/url-from (:request ctx)))]
                 (if (get task :results false)
                   {:body nil
                    :status 303
                    :headers {"Location"
                              (str (http/url-base (:request ctx)) "/results/" id)}}
                   (hal/resource->representation resource :json)))))

(defn represent-result
  [result]
  (let [location (str (http/url-base) "/results/" (:id result) "/")
        self {:rel "self" :href location}
        files (:files result)
        embedded (map (fn [x] {:name (first (s/split (:name x) #"\."))
                               :href (str location (:name x))
                               :type (:mime x)}) files)]
    (assoc
      (dissoc result :files) :_links [self] :_embedded {:_files embedded})))


(defresource result-resource
  :available-media-types ["application/json"]
  :available-charsets ["utf-8"]
  :method-allowed? (request-method-in :options :get)
  :generate-options-header (fn [_] {"Allow" "OPTIONS, GET"})
  :exists? (fn [ctx]
             (let [id (get-in ctx [:request :params :id])
                   result (store/get-result id)]
               (if (nil? result)
                 [false {}]
                 {::result result})))
  :handle-ok (fn [ctx] (json/encode (represent-result (::result ctx)))))

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
               (ANY ["/:id" :id match-uuid] [id] result-resource))
      (context "/tasks" []
               (ANY "/:method" [method] tasks-resource)
               (ANY ["/:method/:id" :id match-uuid] [method id] task-resource)
               (OPTIONS ["/:method/:id/status" :id match-uuid] [] (http/options #{:options :get}))
               (GET ["/:method/:id/status" :id match-uuid] [method id] task-status))
      (GET "/" [] (resp/resource-response "index.html" {:root "public"}))
      (ANY "/*" [] static))))

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
      (log/info "Running server on port" (:port options) ";nREPL running on" (:repl options))
      (defonce repl-server (repl/start-server :port (:repl options)))
      (tasks/initialize)
      (run-server handler {:port (:port options)}))))
