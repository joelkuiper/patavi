(ns clinicico.server.domain
  (:require [clojure.tools.logging :as log]
            [clojure.string :only [replace split] :as s]
            [org.httpkit.server :refer [send! with-channel on-close]]
            [liberator.representation :refer [ring-response]]
            [liberator.core :refer [resource defresource request-method-in]]
            [ring.util.response :as resp]
            [cheshire.core :as json]
            [clinicico.common.util :refer [dissoc-in]]
            [clinicico.server.resource :as hal]
            [clinicico.server.http :as http]
            [clinicico.server.tasks :only [publish-task status task-available?] :as tasks]))

(defn add-slash
  [string]
  (str string (when-not (.endsWith string "/") "/")))

(defn- embedded-files
  [task url]
  (if (task :results)
    (let [result (task :results)
          files (:files result)
          embedded (map (fn [x] {:name (first (s/split (:name x) #"\."))
                                 :href (str url "files/" (:name x))
                                 :type (:mime x)}) files)]
      (assoc-in (dissoc-in task [:results :files]) [:results :_embedded :_files] embedded))
    task))

(defn represent-task
  [task url]
  (let [status-url (str (add-slash url) "status")
        task (embedded-files task url)]
    (-> (hal/new-resource url)
        (hal/add-link :href status-url
                      :rel "status"
                      :websocket (s/replace status-url #"http(s)?" "ws")
                      :comment "Comet long-polling and WebSocket for status updates")
        (hal/add-properties task))))

(def listeners (atom {}))

(defn broadcast-update
  [task]
  (let [id (:id task)
        method (:method task)
        url (str (http/url-base) "/tasks/" method "/" id "/")
        resource (represent-task task url)]
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
    (hal/resource->representation resource :json)))

(defresource tasks-resource
  :available-media-types ["application/json"]
  :allowed-methods [:options :post :get]
  :service-available? (fn [ctx]
                        (tasks/task-available?
                          (get-in ctx [:request :route-params :method])))
  :handle-ok (fn [_] (json/encode {:status "Up and running"}))
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
                 (hal/resource->representation resource :json))))
