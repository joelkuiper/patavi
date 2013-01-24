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

(defn analyze-file [file & args] 
  (let [R (R-connection)
        networkFile (.createFile R (file :filename))]
    (do 
      (io/copy (file :tempfile) networkFile)
      (.close networkFile))
    (.voidEval R (str "network <- read.mtc.network('" (file :filename) "')"))
    (-> (.eval R "network$description") (.asString))
))
