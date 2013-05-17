;; ## Store
;; A simple MongoDB storage for the produced results.
;; Takes any map that includes a results field.

(ns clinicico.server.store
  (:use [clinicico.server.config]
        [clinicico.server.util]
        [monger.core :only [connect! set-db! get-db]]
        [monger.conversion :only [from-db-object]])
  (:require [clojure.walk :as walk]
            [clojure.tools.logging :as log]
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
