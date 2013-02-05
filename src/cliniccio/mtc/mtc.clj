(ns clinicico.mtc.mtc
  (:use [clinicico.util]
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

(defn load-mtc! [R] 
  (.voidEval R "suppressWarnings(require('gemtc',quietly=TRUE))"))

(defn read-network [R] 
  (let [network (R/as-list (.get R "network" nil true)) 
        description (.at network "description")
        data (R/as-list network "data")
        treatments (R/as-list network "treatments")]
    {:description (if-not (.isNull description) (.asString description) "")
     :data (map-cols-to-rows (R/list-to-map data))
     :treatments (map-cols-to-rows (R/list-to-map treatments))}))

(defn- load-network-file 
  [R file] 
  (let [networkFile (.createFile R (file :filename))]
    (do 
      (io/copy (file :tempfile) networkFile)
      (.close networkFile)
      (.assign R "network" (R/parse R (str "read.mtc.network('" (file :filename) "')"))))
    (.removeFile R (file :filename))))

(defn- load-network-json 
  ([R network]
   (let [description (:description network)
         data (:data network) 
         treatments (:treatments network)]
     (do
       (.assign R "description" (R/to-REXPVector description))
       (.assign R "data" (R/RList-as-dataframe (R/map-to-RList (map-rows-to-cols data))))
       (.assign R "treatments" (.eval R (REXPList. (R/map-to-RList (map-rows-to-cols treatments))) nil false))
       (.assign R "network" (R/parse R (str "mtc.network(data, description, treatments)")))))))

(defn load-network! 
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
    {:images (map-cols-to-rows {:url (map #(url-for-img-path %) (map #(.asString (.at (.asList %) "url")) images))
                                :description (map #(.asString (.at (.asList %) "description")) images)})
     :results (parse-results-list results)}))
 
(defn analyze-consistency! [R options] 
  (let [script-file "consistency.R"] 
    (with-open [script (.createFile R script-file)] 
      (io/copy (io/as-file (io/resource (str "R/" script-file))) script))
    (.voidEval R (str "source('"script-file"')"))
    (.removeFile R script-file)
    {:consistency (parse-results (R/parse R "mtc.consistency(network)"))}))

(defn consistency 
  ([params]
   (with-open [R (R/connect)]
     (load-mtc! R) 
     (load-network! R params)
     {:network (read-network R) 
      :results (analyze-consistency! R (get params "options"))})))
