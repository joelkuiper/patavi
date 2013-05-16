(ns clinicico.server.handler
  (:gen-class)
  (:use [org.httpkit.server]
        [compojure.core :only [context ANY GET OPTIONS routes defroutes]]
        [compojure.handler :only [api site]]
        [clinicico.server.util]
        [clinicico.server.middleware]
        [liberator.core :only [resource defresource request-method-in]])
  (:require [clojure.tools.logging :as log]
            [clojure.java.io :only [reader] :as io]
            [clojure.string :only [replace] :as s]
            [ring.util.response :as resp]
            [ring.middleware.reload :as reload]
            [cheshire.core :as json]
            [clinicico.server.resource :as hal]
            [clinicico.server.http :as http]
            [clinicico.server.store :as store]
            [clinicico.server.tasks :only [initialize publish-task
                                           status task-available?] :as tasks]))

(declare in-dev?)

(defn represent-task
  [task url]
  (let [status-url (str url (when-not (.endsWith url "/") "/") "status")
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
     :headers {"Location" url}
     :body (hal/resource->representation resource :json)}))

(def listeners (atom {}))

(defn broadcast-update
  [task]
  (let [id (:id task)
        resource (represent-task task (str (http/url-base) "/tasks/" id "/"))]
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
                    (get-in request [:params :immediate]))
            (broadcast-update (tasks/status id)))
          (on-close channel (fn [status]
                              (swap! listeners dissoc id))))))))

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
                 payload (json/decode-stream
                           (io/reader (get-in ctx [:request :body])))]
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

(defresource result-resource
  :available-media-types ["application/json"]
  :available-charsets ["utf-8"]
  :exists? (fn [ctx]
             (let [id (get-in ctx [:request :params :id])
                   result (store/get-result id)]
               (if (nil? result)
                 [false {}]
                 {::result result})))
  :handle-ok (fn [ctx] (json/encode (::result ctx))))

(def match-uuid #"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
(defn assemble-routes []
  (->
    (routes
      (context "/results" []
               (ANY ["/:id" :id match-uuid] [id] result-resource))
      (context "/tasks" []
               (ANY "/:method" [method] tasks-resource)
               (GET ["/:method/:id/status" :id match-uuid] [method id] task-status)
               (OPTIONS ":/method/:id/status" [] (http/options #{:options :get}))
               (ANY ["/:method/:id" :id match-uuid] [method id] task-resource))
      (GET "/" [] (resp/resource-response "index.html" {:root "public"}))
      (ANY "/*" [] static))))

(def app
  (->
    (assemble-routes)
    (api)
    (ignore-trailing-slash)
    (wrap-cors-request)
    (wrap-request-logger)
    (wrap-exception-handler)
    (wrap-response-logger)))

(defn -main
  [& args]
  (def in-dev? (= "--development" (first args)))
  (let [handler (if in-dev? (reload/wrap-reload app) app)]
    (tasks/initialize)
    (run-server handler {:port 3000})))
