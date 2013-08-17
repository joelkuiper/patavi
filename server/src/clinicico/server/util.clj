(ns clinicico.server.util
  (:gen-class)
  (:require [cheshire.core :refer :all]
            [clojure.java.io :as io]
            [clojure.string :as s])
  (:import (org.joda.time.format.ISODateTimeFormat)
           (com.fasterxml.jackson.core JsonGenerator))
  (:use [cheshire.generate :refer [add-encoder encode-str remove-encoder]]
        clojure.java.io
        clojure.walk))

(add-encoder java.lang.Exception
  (fn [^Exception e ^JsonGenerator jg]
    (.writeStartObject jg)
    (.writeFieldName jg "exception")
    (.writeString jg (.getName (class e)))
    (.writeFieldName jg "message")
    (.writeString jg (.getMessage e))
    (.writeEndObject jg)))
