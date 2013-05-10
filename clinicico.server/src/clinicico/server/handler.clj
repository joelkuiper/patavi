(ns clinicico.server.handler
  (:gen-class)
  (:use [compojure.core :only [context ANY routes defroutes]]
        [compojure.handler :only [api]]
        [hiccup.page :only [html5]]
        [clinicico.server.util :only [wrap-binder static]]
        [clinicico.server.middleware]
        [ring.middleware.json]
        [liberator.core :only [resource defresource request-method-in]])
  (:require [clojure.tools.logging :as log]
            [cheshire.core :as json]
            [clinicico.server.tasks :only [publish-task task-available?] :as tasks]))

(defresource index
  :available-media-types ["text/html"]
  :handle-ok (fn [context]
               (html5 [:head [:title "Clinicico R web-service wrapper"]]
                      [:body
                       [:h1 "Clinicico R web-service wrapper"]])))

(defresource tasks-resource
  :available-media-types ["application/json"]
  :available-charsets ["utf-8"]
  :method-allowed? (request-method-in :post :get)
  :service-available? (fn [ctx] (tasks/task-available? (get-in ctx [:request :route-params :method])))
  :handle-ok (fn [ctx] "Up and running")
  :handle-created (fn [ctx] (json/generate-string (:task ctx)))
  :post! (fn [ctx]
           (let [method (get-in ctx [:request :route-params :method])
                 payload (get-in ctx [:request :body])]
             (assoc ctx :task (tasks/publish-task method (json/parse-string (slurp payload)))))))

(defn assemble-routes []
  (->
    (routes
      (ANY "/" [] index)
      (ANY "/static/*" [] static)
      (ANY "/tasks/:method" [method]
           (-> tasks-resource
               (wrap-binder ::method (str "/" method)))))))

(def app
  (->
    (assemble-routes)
    (api)
    (wrap-request-logger)
    (wrap-exception-handler)
    (wrap-response-logger)))
