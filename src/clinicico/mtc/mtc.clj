(ns clinicico.mtc.mtc
  (:use [clinicico.util.util]
        [clinicico.config]
        [clojure.string :only [upper-case join split]]) 
  (:require [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [clinicico.R.util :as R])
  (:import (org.rosuda.REngine)
           (org.rosuda.REngine REXP
                               REXPList
                               RList)
           (org.rosuda.REngine.Rserve RConnection)))

(defn read-network 
  "Reads the network present in the current `RConnection` and transforms it into a map."
  [R]
  (let [network (R/as-list (.get R "network" nil true)) 
        description (.at network "description")
        data (R/as-list network "data")
        treatments (R/as-list network "treatments")]
    {:description (if-not (.isNull description) (.asString description) "")
     :data (map-cols-to-rows (R/into-map data))
     :treatments (map-cols-to-rows (R/into-map treatments))}))

(defn- load-network-file 
  [R file] 
  (let [networkFile (.createFile R (file :filename))]
    (io/copy (file :tempfile) networkFile)
    (.close networkFile)
    (.assign R "network" (R/parse R (str "read.mtc.network('" (file :filename) "')")))
    (.removeFile R (file :filename))))

(defn- load-network-json 
  [R network]
   (let [description (:description network)
         data (:data network) 
         treatments (:treatments network)]
     (do
       (.assign R "description" 
                (R/into-r description))
       (.assign R "data" 
                (R/r-list-as-dataframe 
                  (R/into-r (map-rows-to-cols data))))
       (.assign R "treatments" 
                (.eval R 
                  (REXPList. 
                    (R/into-r (map-rows-to-cols treatments))) nil false))
       (.assign R "network" 
                (R/parse R (str "mtc.network(data, description, treatments)"))))))

(defn load-network! 
  "Loads a GeMTC network into an `RConnection`.

   - If the params field contains a file it is assumed to be a 
     GeMTC file and proccessed accordingly. 
   - If it is a network it should be in the network JSON format."
  [R params]
  (cond 
    (contains? params "file") (load-network-file R (get params "file"))
    (contains? params "network") (load-network-json R (get params "network"))
    :else (throw (IllegalArgumentException. "Could not load network for data " params))))

(defn- parse-results-list [^RList lst] 
  (let [names (.keys lst)
        conv {"matrix" #(R/parse-matrix %)}]
    (map (fn [k] (let [itm (R/as-list lst k)
                                   data (.at itm "data")
                                   desc (.asString (.at itm "description"))
                                   data-type (.asString (.at itm "type"))]
                               {:name k 
                                :description  desc
                                :data ((get conv data-type) data)})) names)))

(defn- url-for-img-path [path]
 (let [exploded (split path #"\/")
       workspace (first (filter #(re-matches #"conn[0-9]*" %) exploded))
       img (last exploded)]
   (str base-url "generated/" workspace "/" img)))

(defn- parse-results [^REXP results]
  (let [data (.asList results)
        images (.asList (.at data "images"))
        results (.asList (.at data "results"))]
    {:images (map-cols-to-rows 
               {:url (map #(url-for-img-path %) 
                          (map #(.asString (.at (.asList %) "url")) images))
                :description (map #(.asString (.at (.asList %) "description")) images)})
     :results (parse-results-list results)}))
 
(defn- analyze-consistency!
  "Runs a consistency model and returns the used network and results. 
   To produce the relevant results and do other preprocessing 
   please modify the loaded consistency.R script.

   This script should return a list of results with 
   descriptions and a list of images with associated descriptions."
  [R options] 
  (let [script-file "consistency.R"] 
    (with-open [script (.createFile R script-file)] 
      (io/copy (io/as-file (io/resource (str "R/" script-file))) script))
    (.voidEval R (str "source('"script-file"')"))
    (.removeFile R script-file)
    {:consistency (parse-results (R/parse R "mtc.consistency(network)"))}))

(defn consistency 
  ([params]
   (with-open [R (R/connect)]
     (load-network! R params)
     {:network (read-network R) 
      :results (analyze-consistency! R (get params "options"))})))
