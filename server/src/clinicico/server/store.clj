;; ## Store
;; Simple mongodb access and storage of statuses and document
(ns clinicico.server.store
  (:use [clinicico.server.config]
        [clinicico.server.util]
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
   :collection "statuses"})

(connect! mongo-options)
(set-db! (get-db (mongo-options :db)))
(if (not (collection/exists? (mongo-options :collection)))
  (do
    (collection/create (mongo-options :collection) {})
    (collection/ensure-index (mongo-options :collection) {:modified 1} {:expireAfterSeconds 120})))

(defn retrieve
  [id]
  (let [document (collection/find-map-by-id (mongo-options :collection) id)]
    (if document
      (assoc (dissoc document :_id) :id id))))

(defn update!
  [id content]
  (collection/update-by-id (mongo-options :collection) id content))

(defn get-file
  [id filename]
  (-> (gfs/find-one  {:filename  (str id "/" filename)})))

(defn- with-oid
  [id document]
  (assoc document :_id id))

(defn- created-now
  [document]
  (assoc document :created (time/now)))

(defn- modified-now
  [document]
  (assoc document :modified (time/now)))

(def document-validator (validation-set
                        (presence-of :_id)))

(defn insert!
  [id document]
  (let [new-document (created-now
                     (modified-now (with-oid id document)))]
    (if (valid? document-validator new-document)
      (if (ok?
            (collection/insert
              (mongo-options :collection)
              (conv/to-db-object (stringify-keys* new-document))))
        {:id (str (new-document :_id))
         :stored (time/now)}
        (throw (Exception. "Write Failed")))
      (throw (IllegalArgumentException. "Could not save invalid document set")))))

(defn save-file!
  [file path]
  (store-file (make-input-file (io/input-stream (byte-array (get file "content"))))
              (metadata (get file "metadata"))
              (content-type (get file "mime"))
              (filename path)))

(defn save-files!
  [id files]
  (doall
    (map
      (fn [file]
        (save-file! file (str id "/" (get file "name")))) files)))

