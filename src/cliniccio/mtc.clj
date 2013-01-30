(ns cliniccio.mtc
  (:use     [cliniccio.util]) 
  (:require [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [cliniccio.R.util :as R])
  (:import (org.rosuda.REngine)
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

 
(defn consistency [R & args] 
  (do 
    (.voidEval R "model <- mtc.model(network)")
    ;(R/plot R "model")
    ;(R/plot R "network"))
  {:network (read-network R)}))
