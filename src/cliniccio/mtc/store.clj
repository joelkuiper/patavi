(ns cliniccio.mtc.store
  (:import [org.bson.types ObjectId]
           (com.fasterxml.jackson.core JsonGenerator))
  (:use [monger.core :only [connect! set-db! get-db]]
        [monger.result :only [ok?]]
        [monger.conversion :only [from-db-object]]
        [cheshire.custom :only [JSONable]]
        [cliniccio.config]
        [cliniccio.util]
        [validateur.validation])
  (:require [clojure.walk :as walk]
            [monger.collection :as collection]
            [monger.conversion :as conv]
            [monger.util :as util]
            [monger.joda-time]
            [clojure.tools.logging :as log]
            [monger.json]
            [clj-time.core :as time]))

(def mongo-options
  {:host "localhost"  
   :port 27017 
   :db "cliniccio" 
   :results-collection "results"})

(extend org.joda.time.DateTime
  JSONable
  {:to-json (fn [^org.joda.time.DateTime date ^JsonGenerator jg]
    (.writeStartObject jg)
    (.writeFieldName jg "date")
    (.writeString jg (.toString date))
    (.writeEndObject jg))})


(connect! mongo-options)
(set-db! (get-db (mongo-options :db)))

(defn- with-oid [results]
  (assoc results :_id (util/object-id)))

(defn- created-now [results]
  (assoc results :created (time/now)))

(defn- modified-now [result]
  (assoc result :modified (time/now)))

(def result-validator (validation-set
                      (presence-of :_id)
                      (presence-of :network)
                      (presence-of :results)))

(defn get-result [id] 
  (dissoc (collection/find-map-by-id "results" (ObjectId. id)) :_id))

(defn save-result [result]
  (let [new-result (created-now 
                     (modified-now (with-oid result)))]
    (if (valid? result-validator new-result)
      (if (ok? 
            (collection/insert 
              (mongo-options :results-collection) (conv/to-db-object (stringify-keys* new-result))))
        {:results (str base-url "api/result/" (str (new-result :_id)))} 
        (throw (Exception. "Write Failed")))
      (throw (IllegalArgumentException.)))))  
