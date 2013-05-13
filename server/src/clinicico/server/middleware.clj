(ns clinicico.server.middleware
  (:use compojure.core
        ring.util.response
        [cheshire.custom :only [JSONable]]
        [clojure.string :only [upper-case]])
  (:require [clojure.tools.logging :as log])
  (:import (com.fasterxml.jackson.core JsonGenerator)))

(extend java.lang.Exception
  JSONable
  {:to-json (fn [^Exception e ^JsonGenerator jg]
    (.writeStartObject jg)
    (.writeFieldName jg "exception")
    (.writeString jg (.getName (class e)))
    (.writeFieldName jg "message")
    (.writeString jg (.getMessage e))
    (.writeEndObject jg))})

(defn wrap-exception-handler
   " Middleware to handle exceptions thrown by the underlying code.

   - Returns `HTTP/400` when `InvalidArgumentException` was thrown,
     e.g. missing JSON arguments.
   - Returns `HTTP/500` for all unhandled thrown `Exception`.
    "
  [handler]
  (fn [req]
    (try
      (handler req)
      (catch IllegalArgumentException e
        (->
          (response e)
          (status 400)))
      (catch Exception e
        (->
          (response e)
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
