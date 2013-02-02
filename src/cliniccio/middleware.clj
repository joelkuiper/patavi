(ns cliniccio.middleware
  (:use compojure.core
        ring.util.response
        cliniccio.util
        [cheshire.custom :only [JSONable]]
        [clojure.string :only [upper-case]])
  (:require [cliniccio.R.util :as R]
            [clojure.tools.logging :as log])
  (:import (com.fasterxml.jackson.core JsonGenerator)
           (java.util Date)))


;; Make Exeptions JSONable
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
  [handler]
  (fn [req]
    (try
      (handler req)
      (catch Exception e
        (->
          (response e)
          (status 500))))))

(defn wrap-request-logger [handler]
  (fn [req]
    (let [{remote-addr :remote-addr request-method :request-method uri :uri} req]
      (log/debug remote-addr (upper-case (name request-method)) uri)
      (handler req))))

(defn wrap-response-logger
  [handler]
  (fn [req]
    (let [response (handler req)
          {remote-addr :remote-addr request-method :request-method uri :uri} req
          {status :status body :body} response]
      (if (instance? Exception body)
        (log/warn body remote-addr (upper-case (name request-method)) uri "->" status body)
        (log/debug remote-addr (upper-case (name request-method)) uri "->" status))
      response)))

;; Kindly used from https://github.com/hozumi/session-expiry
(defn wrap-session-expiry 
  [handler expire-sec]
  (let [expire-ms (* 1000 expire-sec)]
    (fn [{{timestamp :session_timestamp :as req-session} :session :as request}]
      (let [expired?  (and timestamp (expire? timestamp expire-ms))
            request  (if expired?
                       (assoc request :session {})
                       request)
            response (handler request)]
        (if (contains? response :session)
          (if (response :session)
            ;;write-session and update date
            (assoc-in response [:session :session_timestamp] (Date.)) 
            ;;delete-session because response include {:session nil}
            response) 
          (if (empty? req-session)
            response
            (if expired?
              ;;delete-session because session is expired
              ;;tear-down R connection
              (do 
                (if-not (nil? (:Rserv (response :session))) 
                  (.close (:Rserv (response :session))))
                (assoc response :session nil)
              )
              ;;update date
              (assoc response :session (assoc req-session :session_timestamp (Date.))))))))))

(defn wrap-R-session 
  ;; Adds an Rserv connection to the current session
  ;; This connection is closed after the session expires
  [handler] 
  (fn [req]
    (let [curr (:session req)
          uuid   (or (curr :uuid) (uuid))
          R  (or (curr :Rserv) (R/connect)) 
          session (assoc curr :uuid uuid :Rserv R)]
      (-> (handler req) 
          (assoc :session session)))))