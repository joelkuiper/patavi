(ns cliniccio.mtc.store
  (:use [monger.core :only [connect! set-db! get-db]]
        [monger.result :only [ok?]]
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

(defn save-results [result]
  (let [new-result (created-now 
                     (modified-now (with-oid (sanitize-keys result))))]
    (if (valid? result-validator new-result)
      (if (ok? 
            (collection/insert 
              (mongo-options :results-collection) (conv/to-db-object (walk/keywordize-keys new-result))))
        {:results (str base-url "/api/results/" (str (new-result :_id)))} 
        (throw (Exception. "Write Failed")))
      (throw (IllegalArgumentException.)))))  
