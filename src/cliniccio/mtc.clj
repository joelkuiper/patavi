(ns cliniccio.mtc
  (:require [clojure.java.io :as io]
            [clojure.tools.logging :as log])
  (:import (org.rosuda.REngine)
           (org.rosuda.REngine.Rserve RConnection)))
           
(defn- load-mtc! [conn] 
  (.voidEval conn "suppressWarnings(require('gemtc',quietly=TRUE))"))

(defn R-connection [] 
  (let [conn (try 
               (RConnection.)
               (catch Exception e (throw (Exception. "Could not connect to RServe" e))))]
    (if (.isConnected conn) 
      (do 
        (load-mtc! conn)
        conn))))

(defn load-network [R file] 
  (let [networkFile (.createFile R (file :filename))]
    (do 
      (io/copy (file :tempfile) networkFile)
      (.close networkFile)
      (.voidEval R (str "network <- read.mtc.network('" (file :filename) "')")))
    R))

(defn rlist [data field convert-fn]
  (let [members (.at data field)]
    (if-not (nil? members)
      (seq (convert-fn members))
      nil))) 

(defn transpose-map [maps]
  (let [x (into {} (filter second maps))] ;; Second is false when nil, so this removes nil values
    (map (fn [m] (zipmap (keys x) m)) (apply map vector (vals x)))))

(defn convert-network [file & args] 
  (let [R (load-network (R-connection) file)
        description (.eval R "network$description")
        data (.asList (.eval R "network$data"))
        treatments (.asList (.eval R "network$treatments"))]
    {:description (if-not (.isNull description) (.asString description) "")
     :data (transpose-map {:study (rlist data "study" (comp #(.asStrings %) #(.asFactor %)))
                           :treatment (rlist data "treatment" (comp #(.asStrings %) #(.asFactor %)))
                           :sampleSize (rlist data "sampleSize" #(.asIntegers %))
                           :responders (rlist data "responders" #(.asIntegers %))
                           :mean (rlist data "mean" #(.asDoubles %))
                           :std.dev (rlist data "std.dev" #(.asDoubles %))})
     :treatments (transpose-map {:id (rlist treatments "id" (comp #(.asStrings %) #(.asFactor %)))
                                 :description (rlist treatments "description" (comp #(.asStrings %) #(.asFactor %)))})
     :success true}))
