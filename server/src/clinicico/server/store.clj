;; ## Store
;;  simple MongoDB access for the produced results.

(ns clinicico.server.store
  (:use [clinicico.server.config]
        [clinicico.server.util]
        [monger.core :only [connect! set-db! get-db]]
        [monger.conversion :only [from-db-object]])
  (:require [clojure.walk :as walk]
            [clojure.tools.logging :as log]
            [monger.gridfs :as gfs]
            [monger.collection :as collection]))

(def mongo-options
  {:host (str (:mongo-host config))
   :port (Long. (:mongo-port config))
   :db (str (:mongo-db config))
   :results-collection "results"})

(connect! mongo-options)
(set-db! (get-db (mongo-options :db)))

(defn get-result
  [id]
  (let [result (collection/find-map-by-id "results" id)]
    (if result
      (assoc
        (dissoc result :_id) :id id))))

(defn get-file
  [id filename]
  (-> (gfs/find-one  {:filename  (str id "/" filename)})))
