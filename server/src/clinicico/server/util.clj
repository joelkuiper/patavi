(ns clinicico.server.util
  (:gen-class)
  (:require [cheshire.generate :refer [add-encoder remove-encoder]]
            [cheshire.core :refer :all])
  (:import [com.fasterxml.jackson.core JsonGenerator]
           [org.joda.time.format DateTimeFormat ISODateTimeFormat] ))

(def FORMAT-8601 (ISODateTimeFormat/dateTime))

;; add a JSON encoding function for Joda DateTime objects
(add-encoder org.joda.time.DateTime
             (fn [^org.joda.time.DateTime date-time ^JsonGenerator jg]
               (.writeString jg (.print FORMAT-8601 date-time))))

(add-encoder java.lang.Exception
             (fn [^Exception e ^JsonGenerator jg]
               (.writeStartObject jg)
               (.writeFieldName jg "exception")
               (.writeString jg (.getName (class e)))
               (.writeFieldName jg "message")
               (.writeString jg (.getMessage e))
               (.writeEndObject jg)))
