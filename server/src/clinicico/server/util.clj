(ns clinicico.server.util
  (:gen-class)
  (:require [cheshire.core :refer :all]
            [clojure.java.io :as io]
            [cheshire.generate :refer [add-encoder encode-str remove-encoder]])
  (:import (org.joda.time.format.ISODateTimeFormat)
           (com.fasterxml.jackson.core JsonGenerator))
  (:use
    [ring.util.mime-type :only [ext-mime-type]]
    [compojure.core :only [routes ANY]]
    [liberator.core :only [defresource]]))

(defn wrap-binder [handler key value]
  (fn [request]
    (handler (assoc request key value))))

(add-encoder java.lang.Exception
  (fn [^Exception e ^JsonGenerator jg]
    (.writeStartObject jg)
    (.writeFieldName jg "exception")
    (.writeString jg (.getName (class e)))
    (.writeFieldName jg "message")
    (.writeString jg (.getMessage e))
    (.writeEndObject jg)))

;; Make joda.time JSONable
(add-encoder org.joda.time.DateTime
  (fn [^org.joda.time.DateTime date ^JsonGenerator jg]
    (.writeString jg (.print (org.joda.time.format.ISODateTimeFormat/dateTime) date))))

(defn chop
  "Removes the last character of string."
  [s]
  (subs s 0 (dec (count s))))
