;; ## Store
;; A simple MongoDB storage for the produced results.
;; Takes any map that includes a results field.

(ns clinicico.worker.store
  (:import [org.bson.types ObjectId])
  (:use [clinicico.worker.config]
        [clinicico.worker.util.util]
        [validateur.validation]
        [monger.gridfs :only  [store-file make-input-file filename content-type metadata]]
        [monger.core :only [connect! set-db! get-db]]
        [monger.result :only [ok?]]
        [monger.conversion :only [from-db-object]])
  (:require [clojure.walk :as walk]
            [clojure.tools.logging :as log]
            [clojure.java.io :as io :only [input-stream]]
            [monger.collection :as collection]
            [monger.conversion :as conv]
            [monger.json]
            [monger.gridfs :as gfs]
            [monger.util :as util]
            [monger.joda-time]
            [clj-time.core :as time]))

(def mongo-options
  {:host (str (:mongo-host config))
   :port (Long. (:mongo-port config))
   :db (str (:mongo-db config))
   :results-collection "results"})

(connect! mongo-options)
(set-db! (get-db (mongo-options :db)))

(defn- with-oid
  [id results]
  (assoc results :_id id))

(defn- created-now
  [results]
  (assoc results :created (time/now)))

(defn- modified-now
  [result]
  (assoc result :modified (time/now)))

(def result-validator (validation-set
                        (presence-of :_id)))

(defn save-result
  [id results]
  (let [new-result (created-now
                     (modified-now (with-oid id results)))]
    (if (valid? result-validator new-result)
      (if (ok?
            (collection/insert
              (mongo-options :results-collection)
              (conv/to-db-object (stringify-keys* new-result))))
        {:id (str (new-result :_id))
         :stored (time/now)}
        (throw (Exception. "Write Failed")))
      (throw (IllegalArgumentException. "Could not save invalid result set")))))


(defn- save-file
  [file path]
  (store-file (make-input-file (io/input-stream (byte-array (get file "content"))))
              (metadata (get file "metadata"))
              (content-type (get file "mime"))
              (filename path)))


(defn save-files
  [id files]
  (doall
    (map
      (fn [file]
        (save-file file (str id "/" (get file "name")))) files)))

