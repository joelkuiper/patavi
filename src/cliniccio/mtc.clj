(ns cliniccio.mtc
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
  (let [R (R-connection)] 
))
