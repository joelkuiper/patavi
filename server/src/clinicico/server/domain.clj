(ns clinicico.server.domain
  (:gen-class)
  (:use [org.httpkit.server]
        [liberator.representation :only [ring-response]]
        [liberator.core :only [resource defresource request-method-in]])
  (:require [clojure.tools.logging :as log]
            [clojure.string :only [replace split] :as s]
            [ring.util.response :as resp]
            [clinicico.server.resource :as hal]
            [clinicico.server.http :as http]
            [clinicico.server.store :as store]
            [clinicico.server.tasks :only [publish-task status task-available?] :as tasks]))

(defn add-slash
  [url]
  (str url (when-not (.endsWith url "/") "/")))

(defn represent-task
  [task url]
  (let [status-url (str (add-slash url) "status")
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
          (when (or (contains? #{"failed" "canceled" "completed"} (:status status))
                    (get-in request [:params :latest]))
            (broadcast-update status))
          (on-close channel (fn [_] (swap! listeners dissoc id))))))))

(defn handle-new-task
  [ctx]
  (let [task (:task ctx)
        url (http/url-from (:request ctx) (:id task))
        resource (represent-task task url)]
    (ring-response {:status 202
                    :headers {"Location" url
                              "Content-Type" "application/json"}
                    :body (hal/resource->representation resource :json)})))

(defresource tasks-resource
  :available-media-types ["application/json"]
  :allowed-methods [:options :post :get]
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
  :allowed-methods [:options :delete :get]
  :exists? (fn [ctx] (not (nil?
                            (tasks/status
                              (get-in ctx [:request :params :id])))))
  :handle-ok (fn [ctx]
               (let [id (get-in ctx [:request :params :id])
                     task (tasks/status id)
                     resource (represent-task task (http/url-from (:request ctx)))]
                 (if (get task :results false)
                   (ring-response {:body nil
                                   :status 303
                                   :headers {"Location"
                                             (str (http/url-base (:request ctx)) "/results/" id)}})
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
  :allowed-methods [:options :get]
  :exists? (fn [ctx]
             (let [id (get-in ctx [:request :params :id])
                   result (store/get-result id)]
               (if (nil? result)
                 [false {}]
                 {::result result})))
  :handle-ok (fn [ctx] (json/encode (represent-result (::result ctx)))))

