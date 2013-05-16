(ns clinicico.server.middleware
  (:use compojure.core
        ring.util.response
        [clojure.string :only [upper-case]])
  (:require [clojure.tools.logging :as log]
            [cheshire.core :as json]
            [clinicico.server.util :as util]))

(defn- wrap-exception
  [req e]
  (log/error (.getMessage e))
  (json/encode e))

(defn wrap-exception-handler
   " Middleware to handle exceptions thrown by the underlying code.

   - Returns `HTTP/400` when `InvalidArgumentException` was thrown,
     e.g. missing JSON arguments.
   - Returns `HTTP/500` for all unhandled thrown `Exception`."
  [handler]
  (fn [req]
    (try
      (handler req)
      (catch IllegalArgumentException e
        (->
          (response (wrap-exception req e))
          (status 400)))
      (catch Exception e
        (->
          (response (wrap-exception req e))
          (status 500))))))

(defn wrap-request-logger
  "Logs the request. Log settings can be set in the `resources/log4j.properties.`"
  [handler]
  (fn [req]
    (let [{remote-addr :remote-addr request-method :request-method uri :uri} req]
      (log/debug remote-addr (upper-case (name request-method)) uri)
      (handler req))))

(defn wrap-response-logger
  "Logs the response and the `Exception` body when one was present"
  [handler]
  (fn [req]
    (let [response (handler req)
          {remote-addr :remote-addr request-method :request-method uri :uri} req
          {status :status body :body} response]
      (if (instance? Exception body)
        (log/warn body remote-addr (upper-case (name request-method)) uri "->" status body)
        (log/debug remote-addr (upper-case (name request-method)) uri "->" status))
      response)))

(defn- with-uri-rewrite
  "Rewrites a request uri with the result of calling f with the
   request's original uri.  If f returns nil the handler is not called."
  [handler f]
  (fn [request]
    (let [uri (:uri request)
          rewrite (f uri)]
      (if rewrite
        (handler (assoc request :uri rewrite))
        nil))))

(defn- uri-snip-slash
  "Removes a trailing slash from all uris except \"/\"."
  [uri]
  (if (and (not (= "/" uri))
           (.endsWith uri "/"))
    (util/chop uri)
    uri))

(defn ignore-trailing-slash
  "Makes routes match regardless of whether or not a uri ends in a slash."
  [handler]
  (with-uri-rewrite handler uri-snip-slash))

