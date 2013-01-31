(ns cliniccio.mtc
  (:use [cliniccio.util]
        [clojure.string :only [upper-case join split]]) 
  (:require [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [cliniccio.R.util :as R])
  (:import (org.rosuda.REngine)
           (org.rosuda.REngine REXP
                               RList)
           (org.rosuda.REngine.Rserve RConnection)))

(defn load-mtc! [R] 
  (.voidEval R "suppressWarnings(require('gemtc',quietly=TRUE))"))

(defn load-network-file [R file] 
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
     :data (map-cols-to-rows {:study (R/in-list data "study" (comp #(.asStrings %) #(.asFactor %)))
                           :treatment (R/in-list data "treatment" (comp #(.asStrings %) #(.asFactor %)))
                           :sampleSize (R/in-list data "sampleSize" #(.asIntegers %))
                           :responders (R/in-list data "responders" #(.asIntegers %))
                           :mean (R/in-list data "mean" #(.asDoubles %))
                           :std.dev (R/in-list data "std.dev" #(.asDoubles %))})
     :treatments (map-cols-to-rows {:id (R/in-list treatments "id" (comp #(.asStrings %) #(.asFactor %)))
                                 :description (R/in-list treatments "description" (comp #(.asStrings %) #(.asFactor %)))})}))

(defn parse-results-list [lst] 
  (let [names (.keys lst)
        conv {"matrix" #(R/parse-matrix %)}]
    (map (fn [k] (let [itm (.asList (.at lst k))
                       data (.at itm "data")
                       data-type (.asString (.at itm "type"))]
                    (log/debug "got results for" k " with " data " as " data-type)
                    ((get conv data-type) data)
                   )) names)))

(defn parse-results [^REXP results]
  (let [data (.asList results)]
    {:images (R/in-list data "images" #(.asStrings %))
     :results (parse-results-list (.asList (.at data "results")))}))
 
(defn consistency [R & args] 
  (let [script-file "consistency.R"] 
    (with-open [script (.createFile R script-file)] 
      (io/copy (io/as-file (io/resource (str "R/" script-file))) script))
    (.voidEval R (str "source('"script-file"')"))
    (.removeFile R script-file)
    {:results (parse-results (R/parse R "mtc.consistency(network)"))}))
