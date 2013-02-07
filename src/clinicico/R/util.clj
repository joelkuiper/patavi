;; ## R 
;; R interoperability is provided by [Rserve](http://www.rforge.net/Rserve/). 
;; RServe provides both a TCP/IP server for calling R functionality as well as
;; a Java library for convenient access to a Rserve.  
;;
;; The functions below are mostly utilities for converting back and forth
;; between native Clojure reorientations (maps and vectors) and RServes'
;; classes.  

(ns clinicico.R.util
  (:use     [clinicico.util]) 
  (:require [clojure.java.io :as io])
  (:import (org.rosuda.REngine)
           (org.rosuda.REngine REXP RList)
           (org.rosuda.REngine REXPDouble REXPLogical REXPFactor REXPInteger REXPString REXPGenericVector)
           (org.rosuda.REngine.Rserve RConnection)))

 
(defn connect 
 "Connect to an RServe instance. Each connection creates it's own workspace
  and forks the existing R process, effectively sandboxing the R operations
  Returns an [RConnection](http://rforge.net/org/doc/org/rosuda/REngine/Rserve/RConnection.html) 
  which is referred to as R in the code"
 [] 
  (let [conn (try 
               (RConnection.)
               (catch Exception e (throw (Exception. "Could not connect to RServe" e))))]
    (if (.isConnected conn) 
      conn)))

(defn as-list 
  "Converts a 
   [REXPGenericVector](http://rforge.net/org/doc/org/rosuda/REngine/REXPGenericVector.html), 
   or a nested list, to an [RList](http://rforge.net/org/doc/org/rosuda/REngine/RList.html)."
  ([data] (.asList ^REXPGenericVector data))
  ([data $field] (as-list (.at ^RList data $field))))

(defn- convert-fn 
  "Conditionally converts a [REXPVector](http://rforge.net/org/doc/org/rosuda/REngine/REXPVector.html) to primitives"
  ^{:todo "make sure this is complete"}
  [field] 
  (cond 
    (instance? REXPFactor field) (comp #(.asStrings %) #(.asFactor ^REXPFactor %)) ;; REXPFactor is-a REXPInteger, so the order matters
    (instance? REXPInteger field) #(.asIntegers ^REXPInteger %)
    (instance? REXPDouble field) #(.asDoubles ^REXPDouble %)
    :else (throw (Exception. (str "Could not convert field " field)))))

(defn in-list 
  "Returns a field in a list as Clojure sequence (seq) using the convert-fn.
   Rough equivalent to R's data$field accessor" 
  [data $field]
  (let [members (.at ^RList data $field)]
    (if-not (nil? members)
      (seq ((convert-fn members) members))
      nil)))

(defn parse [^RConnection R cmd] 
  (.parseAndEval R cmd nil true))

(defn plot 
  "Plots an existing R variable (must be present in the RConnection!)
   Outputs the results to an equally named file in the current workdir. 
   Requires Cairo package to be present and loaded."
  [^RConnection R item] 
  (let [img-dev (parse R (str "try(png('"item".png'))"))]
    (if (.inherits img-dev "try-error")
      (throw (Exception. (str "Could not initiate image device: " (.asString img-dev))))
      (.voidEval R (str "plot(" item "); dev.off()")))))

(defn parse-matrix 
  "Parses a (named) matrix (2d-list in R) to a map with the rows as a map between the labels and the values.
   If not a named matrix substitutes the missing names with an incrementing index starting with 1" 
  [matrix] 
  (let [dimnames (.getAttribute matrix "dimnames")
        labels  (map (fn [ls] (if-not (.isNull ls) (seq (.asStrings ls)) nil)) (.asList dimnames))
        data (map seq (.asDoubleMatrix matrix))]
    (into {} 
          (mapcat (fn [[k v]] {k (zipmap (or (second labels) (range 1 (inc (count v)))) v)}) 
                  (zipmap (or (first labels) (range 1 (inc (count data)))) data)))))

(defn list-to-map [data]
  (let [ks (.keys ^RList data)] 
    (zipmap ks 
            (map (fn [k] 
                   (let [field (.at ^RList data k)]
                     (seq ((convert-fn field) field)))) ks))))

(defn- factor-indexes [lst] 
  (let [levels (sort (distinct lst))]
    (map #(inc (.indexOf levels %)) lst)))

(defn to-REXPVector
  "Converts a sequential or a primitive to a subclass of [REXPVector](http://rforge.net/org/doc/org/rosuda/REngine/REXPVector.html)"
  [data-seq]
  (let [is-seq (sequential? data-seq)
        el (if is-seq (first data-seq) data-seq)]
    (cond 
      (instance? Integer el) 
        (REXPInteger. (if is-seq (int-array data-seq) (int el))) 
      (instance? Boolean el) 
        (REXPLogical. (if is-seq (boolean-array data-seq) (boolean el))) 
      (instance? Double el) 
        (REXPDouble. (if is-seq (double-array data-seq) (double el))) 
      (and (instance? String el) is-seq (every? #(re-matches #"\w*" %) data-seq)) 
        (REXPFactor. (int-array (factor-indexes data-seq)) (into-array String (sort (distinct data-seq)))) 
      (instance? String el) 
        (REXPString. (if is-seq (into-array String data-seq) el)) 
      :else (throw (IllegalArgumentException. (str "Don't know how to parse " (class el)))))))

(defn map-to-RList [data]
  "Converts a map to an RList, using the to-REXPVector 
   to convert underlying elements to REXPVectors"
  (RList. 
    (map to-REXPVector (vals data)) (into-array String (keys data))))

(defn RList-as-dataframe [RList] 
  (REXP/createDataFrame RList))
