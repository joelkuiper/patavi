(ns clinicico.R.analysis
  (:use validateur.validation
        clojure.walk
        clinicico.config
        clinicico.util.util)
  (:require [clinicico.R.util :as R]
            [clojure.java.io :as io]
            [clojure.string :as strs]
            [clojure.tools.logging :as log])
  (:import (org.rosuda.REngine REXP RList)
           (org.rosuda.REngine.Rserve RConnection)
           (org.rosuda.REngine REXPDouble REXPLogical 
                               REXPFactor REXPInteger 
                               REXPString REXPGenericVector
                               REXPNull REngineException)))

(def validators (atom {"consistency" (validation-set 
                                       (presence-of :network))}))

(defn copy-to-r! 
  [^RConnection R file filename]
  (with-open [r-file (.createFile R filename)] 
    (io/copy file r-file)))

(defn load-analysis! 
  "Finds the R file with the associated analysis 
   name and load its into an RConnection."
  [^RConnection R analysis]
  (let [script (io/as-file (io/resource (str "R/" analysis ".R")))]
    (if (nil? script)
      (throw (IllegalArgumentException. (str "Could not find specified analysis " analysis)))
      (do
        (copy-to-r! R script analysis) 
        (.voidEval R (str "source('"script"')"))
        (.removeFile R analysis)))))

(defn- parse-image 
  [image]
  (let [content (R/in-list image "image") 
        mime (R/in-list image "mime")
        metadata (R/in-list image "metadata")]
  {:content (io/input-stream (byte-array content)) 
   :mime mime 
   :metadata metadata}))

(defn- parse-results-list [^RList lst] 
  (let [names (.keys lst)
        conv {"matrix" #(R/parse-matrix %)
              "image" #(parse-image %)
              "data.frame" #(map-cols-to-rows (R/into-clj %))}]
    (doall (map (fn [k] (let [itm (R/as-list (.at lst k))
                       data (.at itm "data")
                       desc (R/into-clj (.at itm "description"))
                       data-type (R/into-clj (.at itm "type"))]
                   {:name k 
                    :description  desc
                    :data ((get conv data-type R/into-clj) data)})) names))))

(defn- parse-results [^REXP results]
  (try 
    (parse-results-list (R/as-list results))
    (catch Exception e 
      (do (log/debug e)
          (R/into-clj results))))) ; Fallback to generic structure

(defn dispatch 
  [analysis params]
  (if-not (valid? (get @validators analysis (validation-set)) params)
    (throw (IllegalArgumentException. 
             (str "Provided parameters were not valid for analysis " analysis)))
    (let [files (select-keys params (for [[k v] params :when (contains? v :file)] k))
          options (into {} (map (fn [[k v]]
                                  (if (contains? v :file) 
                                    [k {"file" (get-in v [:file :filename])}] 
                                    [k v])) params))]
      (with-open [^RConnection R (R/connect)]
        (doall (map 
                 (fn [[k v]] 
                   (copy-to-r! R (get-in v [:file :tempfile]) (get-in v [:file :filename]))) files))
        (load-analysis! R analysis)
        (R/assign R "params" options)
        {:results {(keyword analysis) (parse-results (R/parse R (str analysis "(params)") false))}}))))
