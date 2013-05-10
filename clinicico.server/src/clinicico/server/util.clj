(ns clinicico.server.util
  (:require
    [clojure.java.io :as io])
  (:use
    [ring.util.mime-type :only [ext-mime-type]]
    [compojure.core :only [routes ANY]]
    [liberator.core :only [defresource]]))

(defn wrap-binder [handler key value]
  (fn [request]
    (handler (assoc request key value))))

(let [static-dir (io/file "resources/public")]
  (defresource static
    :available-media-types
    #(let [path (get-in % [:request :route-params :*])]
       (if-let [mime-type (ext-mime-type path)]
         [mime-type]
         []))
    :exists?
    #(let [path (get-in % [:request :route-params :*])]
       (let [f (io/file static-dir path)]
         [(.exists f) {::file f}]))
    :handle-ok (fn [{f ::file}] f)
    :last-modified (fn [{f ::file}] (.lastModified f))))
