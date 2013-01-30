(ns cliniccio.R.util
  (:use     [cliniccio.util]) 
  (:require [clojure.java.io :as io])
  (:import (org.rosuda.REngine)
           (org.rosuda.REngine.Rserve RConnection)))
           
(defn connect [] 
  (let [conn (try 
               (RConnection.)
               (catch Exception e (throw (Exception. "Could not connect to RServe" e))))]
    (if (.isConnected conn) 
      conn)))

(defn in-list  [data $field convert-fn]
  (let [members (.at data $field)]
    (if-not (nil? members)
      (seq (convert-fn members))
      nil)))

(defn parse [R cmd] 
  (.parseAndEval R cmd nil true))

(defn plot [R item] 
  (let [img-dev (parse R (str "try(png('"item".png'))"))]
    (if (.inherits img-dev "try-error")
      (throw (Exception. (str "Could not initiate image device: " (.asString img-dev))))
      (.voidEval R (str "plot(" item "); dev.off()")))))
