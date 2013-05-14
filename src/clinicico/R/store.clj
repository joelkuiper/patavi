;; ## Store
;; A simple MongoDB storage for the produced results.
;; Takes any map that includes a results field.

(ns clinicico.R.store
  (:import [org.bson.types ObjectId])
  (:use [monger.core :only [connect! set-db! get-db]]
        [monger.result :only [ok?]]
        [monger.conversion :only [from-db-object]]
        [monger.gridfs :only [store-file make-input-file filename content-type metadata]]
        [clinicico.config]
        [clinicico.util.util]
        [validateur.validation])
  (:require [clojure.walk :as walk]
            [clojure.tools.logging :as log]
            [monger.collection :as collection]
            [monger.conversion :as conv]
            [monger.json]
            [monger.util :as util]
            [monger.joda-time]
            [monger.gridfs :as gfs]
            [clj-time.core :as time]))

(def mongo-options
  {:host (str (:mongo-host *config*))
   :port (Long. (:mongo-port *config*))
   :db (str (:mongo-db *config*))
   :results-collection "results"})

(connect! mongo-options)
(set-db! (get-db (mongo-options :db)))

(defn- with-oid
  [results]
  (assoc results :_id (util/object-id)))

(defn- created-now
  [results]
  (assoc results :created (time/now)))

(defn- modified-now
  [result]
  (assoc result :modified (time/now)))

(def result-validator (validation-set
                      (presence-of :_id)
                      (presence-of :body)))

(defn- get-filename
  [file]
  (let [ext (get-in file [:data :metadata "format"])
        name (:name file)]
    (str name "." ext)))

(defn- save-file
  [file path]
    (store-file (make-input-file (get-in file [:data :content]))
                (metadata (get-in file [:data :metadata]))
                (content-type (get-in file [:data :mime]))
                (filename path)))

(defn- get-path
  [id directory file]
 (str "result/" id "/file/" directory "/" file))

(defn save-files-for-result
  [result id analysis]
  (let [filter-fn (fn [x] (not (nil? (get-in x [:data :mime]))))
        files (filter filter-fn result)
        path-fn (fn [file] (get-path id analysis (get-filename file)))]
    (doall (map (fn [file] (save-file file (path-fn file))) files))
    (concat (remove filter-fn result)
      (map (fn [file]
        {:name (:name file)
         :description (:description file)
         :url (str api-url (path-fn file))}) files))))

(defn save-files
  [results]
  (assoc results :results
    (into {} (map (fn [[k v]]
      {k (save-files-for-result v (:_id results) (name k))}) (results :results)))))

(defn save-result
  [results]
  (let [new-result (created-now
                     (modified-now (with-oid results)))]
    (if (valid? result-validator new-result)
      (if (ok?
            (collection/insert
              (mongo-options :results-collection)
              (conv/to-db-object (stringify-keys* new-result))))
        {:results (str api-url "result/" (str (new-result :_id)))
         :id (str (new-result :_id))
         :completed (time/now)}
        (throw (Exception. "Write Failed")))
      (throw (IllegalArgumentException. "Could not save invalid result set")))))

(defn get-result
  [id]
  (dissoc (collection/find-map-by-id "results" (ObjectId. id)) :_id))

(defn get-file
  [id analysis filename]
  (let [f (-> (gfs/find-one {:filename (get-path id analysis filename)}))]
    (if (nil? f)
      (throw (clinicico.ResourceNotFound.))
      f)))
