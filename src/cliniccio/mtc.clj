(ns cliniccio.mtc
  (:require [clojure.java.io :as io])
  (:import (org.rosuda.REngine)
           (org.rosuda.REngine.Rserve RConnection)))
           
(defn- load-mtc! [conn] 
  (.voidEval conn "suppressWarnings(require('gemtc',quietly=TRUE))")
)

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

(defn convert-network [file & args] 
  (let [R (load-network (R-connection) file)
        description (.eval R "network$description")
        data (.asList (.eval R "network$data"))]
    {:description (if-not (.isNull description) (.asString description) "")
     :data {:study (seq (.asStrings (.asFactor (.at data "study" ))))
            :sampleSize (seq (.asIntegers (.at data "sampleSize" )))}
     :success true}))
