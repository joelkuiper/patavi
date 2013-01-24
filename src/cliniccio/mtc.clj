(ns cliniccio.mtc
  (:import (org.rosuda.REngine)
           (org.rosuda.REngine.Rserve RConnection)))
           
(defn- load-mtc! [conn] 
  (.voidEval conn "suppressWarnings(require('gemtc',quietly=TRUE))")
)

(defn R-connection [] 
  (let [conn (RConnection.)]
    (if (.isConnected conn) 
      (do 
        (load-mtc! conn)
        conn  
      )
      (throw (Exception. "Could not connect to RServe")))))

(defn analyze-file [file & args] 
  (let [R (R-connection)] 
))
