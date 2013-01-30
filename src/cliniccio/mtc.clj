(ns cliniccio.mtc
  (:use     [cliniccio.util]) 
  (:require [clojure.java.io :as io]
            [clojure.tools.logging :as log])
  (:import (org.rosuda.REngine)
           (org.rosuda.REngine.Rserve RConnection)))
           
(defn R-connection [] 
  (let [conn (try 
               (RConnection.)
               (catch Exception e (throw (Exception. "Could not connect to RServe" e))))]
    (if (.isConnected conn) 
      conn)))

(defn rlist [data field convert-fn]
  (let [members (.at data field)]
    (if-not (nil? members)
      (seq (convert-fn members))
      nil)))

(defn reval [R cmd] 
  (.parseAndEval R cmd nil true))

(defn load-network-file [R file] 
  (let [networkFile (.createFile R (file :filename))]
    (do 
      (io/copy (file :tempfile) networkFile)
      (.close networkFile)
      (.assign R "network" (reval R (str "read.mtc.network('" (file :filename) "')"))))
      (.removeFile R (file :filename))
    R))

(defn read-network [R & args] 
  (let [network (.asList (.get R "network" nil true)) 
        description (.at network "description")
        data (.asList (.at network "data"))
        treatments (.asList (.at network "treatments"))]
    {:description (if-not (.isNull description) (.asString description) "")
     :data (map-cols-to-rows {:study (rlist data "study" (comp #(.asStrings %) #(.asFactor %)))
                           :treatment (rlist data "treatment" (comp #(.asStrings %) #(.asFactor %)))
                           :sampleSize (rlist data "sampleSize" #(.asIntegers %))
                           :responders (rlist data "responders" #(.asIntegers %))
                           :mean (rlist data "mean" #(.asDoubles %))
                           :std.dev (rlist data "std.dev" #(.asDoubles %))})
     :treatments (map-cols-to-rows {:id (rlist treatments "id" (comp #(.asStrings %) #(.asFactor %)))
                                 :description (rlist treatments "description" (comp #(.asStrings %) #(.asFactor %)))})}))

(defn rplot [R item] 
  (let [img-dev (reval R (str "try(png('"item"'.png))"))]
    (if (.inherits img-dev "try-error")
      (throw (Exception. (str "Could not initiate image device: " (.asString img-dev))))
      (.voidEval R (str "plot(" item "); dev.off()")))))
 
(defn consistency [R & args] 
  (do 
    (.voidEval R "model <- mtc.model(network)")
    (rplot R "model")
    (rplot R "network")
     {:id (.asString (reval R "getwd()"))
       :network (read-network R)}))
