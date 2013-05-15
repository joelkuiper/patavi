(ns clinicico.server.handler
  (:gen-class)
  (:use [compojure.core :only [context ANY routes defroutes]]
        [compojure.handler :only [api site]]
        [org.httpkit.server :only  [run-server]]
            [clinicico.server.util]
            [clinicico.server.middleware]
        [hiccup.page :only [html5]]
        [liberator.core :only [resource defresource request-method-in]])
  (:require [clojure.tools.logging :as log]
            [clojure.java.io :only [reader] :as io]
            [ring.middleware.reload :as reload]
            [cheshire.core :as json]
            [langohr.exchange  :as le]
            [langohr.core      :as rmq]
            [langohr.channel   :as lch]
            [clinicico.server.resource :as hal]
            [clinicico.server.http :as http]
            [clinicico.server.tasks :only [initialize publish-task
                                           status task-available?] :as tasks]))


(declare in-dev?)

(defresource index
  :available-media-types ["text/html"]
  :handle-ok (fn [context]
               (html5 [:head [:title "Clinicico R web-service wrapper"]]
                      [:body
                       [:h1 "Clinicico R web-service wrapper"]])))

(defn represent-task
  [task url]
  (-> (hal/new-resource url)
      (hal/add-properties task)))

(defn handle-new-task [ctx]
  (let [task (:task ctx)
        url (http/url-from (:request ctx) (:id task))
        resource (represent-task task url)]
    {:status 202
     :headers {"Location" url}
     :body (hal/resource->representation resource :json)}))

(defresource tasks-resource
  :available-media-types ["application/json"]
  :available-charsets ["utf-8"]
  :method-allowed? (request-method-in :post :get)
  :service-available? (fn [ctx]
                        (tasks/task-available? (get-in ctx [:request :route-params :method])))
  :handle-ok (fn [ctx] "Up and running")
  :handle-created handle-new-task
  :post! (fn [ctx]
           (let [method (get-in ctx [:request :route-params :method])
                 payload (json/decode-stream (io/reader (get-in ctx [:request :body])))]
             (assoc ctx :task (tasks/publish-task method payload)))))

(defresource task-resource
  :available-media-types ["application/json"]
  :available-charsets ["utf-8"]
  :method-allowed? (request-method-in :delete :get)
  :exists? (fn [ctx] (not (nil? (tasks/status (get-in ctx [:request ::id])))))
  :handle-ok (fn [ctx]
               (let [task (tasks/status (get-in ctx [:request ::id]))
                     url (http/url-from (:request ctx))]
                 (hal/resource->representation (represent-task task url) :json))))

(def match-uuid #"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")

(defn assemble-routes []
  (->
    (routes
      (ANY "/" [] index)
      (ANY "/static/*" [] static)
      (ANY "/tasks/:method" [method] tasks-resource)
      (ANY ["/tasks/:method/:id" :id match-uuid] [method id]
           (-> task-resource
               (wrap-binder ::id id))))))

(def app
  (->
    (assemble-routes)
    (api)
    (ignore-trailing-slash)
    (wrap-request-logger)
    (wrap-exception-handler)
    (wrap-response-logger)))


(defn -main [& args] ;; entry point
  (def in-dev? (= "--development" (first args)))
  (let [handler (if in-dev? (reload/wrap-reload app) app)]
    (println in-dev?)
    (tasks/initialize)
    (run-server handler {:port 3000})))
