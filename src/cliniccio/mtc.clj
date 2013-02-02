(ns cliniccio.mtc
  (:use [cliniccio.util]
        [clojure.string :only [upper-case join split]]) 
  (:require [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [cliniccio.R.util :as R]
            [cliniccio.http :as http])
  (:import (org.rosuda.REngine)
           (org.rosuda.REngine REXP
                               RList)
           (org.rosuda.REngine.Rserve RConnection)))

(defn load-mtc! [R] 
  (.voidEval R "suppressWarnings(require('gemtc',quietly=TRUE))"))

(defn load-network-file! [R file] 
  (let [networkFile (.createFile R (file :filename))]
    (do 
      (io/copy (file :tempfile) networkFile)
      (.close networkFile)
      (.assign R "network" (R/parse R (str "read.mtc.network('" (file :filename) "')"))))
      (.removeFile R (file :filename))
    R))

(defn read-network [R & args] 
  (let [network (.asList (.get R "network" nil true)) 
        description (.at network "description")
        data (.asList (.at network "data"))
        treatments (.asList (.at network "treatments"))]
    {:description (if-not (.isNull description) (.asString description) "")
     :data (map-cols-to-rows (R/list-to-map data))
     :treatments (map-cols-to-rows {:id (R/in-list treatments "id")
                                    :description (R/in-list treatments "description")})}))

(defn parse-results-list [^RList lst] 
  (let [names (.keys lst)
        conv {"matrix" #(R/parse-matrix %)}]
    (map (fn [k] (let [itm (.asList (.at lst k))
                                   data (.at itm "data")
                                   desc (.asString (.at itm "description"))
                                   data-type (.asString (.at itm "type"))]
                               {:name k 
                                :description  desc
                                :data ((get conv data-type) data)})) names)))

(defn url-for-img-path [req path]
 (let [location {:scheme (req :scheme) 
                 :server-name (req :server-name) 
                 :server-port (req :server-port)}
       exploded (split path #"\/")
       workspace (first (filter #(re-matches #"conn[0-9]*" %) exploded))
       img (last exploded)]
   (str (http/url-from location) "/generated/" workspace "/" img)))

(defn parse-results [req ^REXP results]
  (let [data (.asList results)
        images (.asList (.at data "images"))
        results (.asList (.at data "results"))]
    {:images (map-cols-to-rows {:url (map #(url-for-img-path req %) (map #(.asString (.at (.asList %) "url")) images))
                                :description (map #(.asString (.at (.asList %) "description")) images)})
     :results (parse-results-list results)}))
 
(defn analyze-consistency! [req R & args] 
  (let [script-file "consistency.R"] 
    (with-open [script (.createFile R script-file)] 
      (io/copy (io/as-file (io/resource (str "R/" script-file))) script))
    (.voidEval R (str "source('"script-file"')"))
    (.removeFile R script-file)
    {:results (parse-results req (R/parse R "mtc.consistency(network)"))}))
