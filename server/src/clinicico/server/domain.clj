(ns clinicico.server.domain
  (:require [clojure.tools.logging :as log]
            [clojure.string :only [replace split] :as s]
            [clojure.core.async :as async :refer :all]
            [clj-wamp.server :as wamp]
            [ring.util.response :as resp]
            [org.httpkit.server :as http-kit]
            [clinicico.server.store :as store]
            [clinicico.common.util :refer [dissoc-in]]
            [clinicico.server.tasks :only [publish-task status task-available?] :as tasks]))

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

(def base "http://myapp/")
(def service-rpc-uri (str base "rpc#"))
(def service-status-uri (str base "status#"))

(defn service-run-rpc [method data]
  (log/info "Long Run RPC called" data method)
  (doseq [i (range 10)]
    (Thread/sleep 600)
    ; Only send status to the client who called
    (log/debug wamp/*call-sess-id*)
    (wamp/emit-event! service-status-uri (* i 10) [wamp/*call-sess-id*]))
  {:result true})

(def origin-re #"http://.*")

(defn handle-tasks
  "Returns a http-kit websocket handler with wamp subprotocol"
  [request]
  (let [method (get-in request [:route-params :method])]
    (wamp/with-channel-validation request channel origin-re
      (wamp/http-kit-handler channel
                             {:on-call {service-rpc-uri (partial service-run-rpc method)}
                              :on-subscribe {service-status-uri true}
                              :on-publish {service-status-uri true
                                           (str service-status-uri "pub-only") true}}))))

(defn get-file [id file]
  (let [record (store/get-file id file)]
    (if (nil? record)
      (resp/not-found nil)
      (-> (resp/response (.getInputStream record))
          (resp/content-type (:content-type (.getContentType record)))
          (resp/header "Content-Length" (.getLength record))))))
