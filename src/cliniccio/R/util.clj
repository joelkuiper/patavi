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

(defn- convert-fn [field] 
  (cond 
    (instance? org.rosuda.REngine.REXPFactor field) (comp #(.asStrings %) #(.asFactor ^org.rosuda.REngine.REXPFactor %))
    (instance? org.rosuda.REngine.REXPInteger field) #(.asIntegers ^org.rosuda.REngine.REXPInteger %)
    (instance? org.rosuda.REngine.REXPDouble field) #(.asDoubles ^org.rosuda.REngine.REXPDouble %)
    :else (throw (Exception. (str "Could not convert field " field)))))

(defn as-list 
  ([data] (.asList ^org.rosuda.REngine.REXPGenericVector data))
  ([data $field] (as-list (.at ^org.rosuda.REngine.RList data $field))))

(defn in-list  [data $field]
  (let [members (.at ^org.rosuda.REngine.RList data $field)]
    (if-not (nil? members)
      (seq ((convert-fn members) members))
      nil)))

(defn parse [R cmd] 
  (.parseAndEval R cmd nil true))

(defn plot [R item] 
  (let [img-dev (parse R (str "try(png('"item".png'))"))]
    (if (.inherits img-dev "try-error")
      (throw (Exception. (str "Could not initiate image device: " (.asString img-dev))))
      (.voidEval R (str "plot(" item "); dev.off()")))))

(defn parse-matrix [matrix] 
  (let [dimnames (.getAttribute matrix "dimnames")
        labels  (map (fn [ls] (if-not (.isNull ls) (seq (.asStrings ls)) nil)) (.asList dimnames))
        data (map seq (.asDoubleMatrix matrix))]
    (into {} 
          (mapcat (fn [[k v]] {k (zipmap (or (second labels) (range 1 (inc (count v)))) v)}) 
                  (zipmap (or (first labels) (range 1 (inc (count data)))) data)))))

(defn list-to-map [data]
  (let [ks (.keys ^org.rosuda.REngine.RList data)] 
    (zipmap ks 
            (map (fn [k] 
                   (let [field (.at ^org.rosuda.REngine.RList data k)]
                     (seq ((convert-fn field) field)))) ks))))

(defn to-REXPVector
  [data-seq]
  (let [is-seq (sequential? data-seq)
        el (if is-seq (first data-seq) data-seq)]
    (cond 
      (instance? Integer el) (org.rosuda.REngine.REXPInteger. (if is-seq (int-array data-seq) (int el))) 
      (instance? Boolean el) (org.rosuda.REngine.REXPLogical. (if is-seq (boolean-array data-seq) (boolean el))) 
      (instance? Double el) (org.rosuda.REngine.REXPDouble. (if is-seq (double-array data-seq) (double el))) 
      (instance? String el) (org.rosuda.REngine.REXPString. (if is-seq (into-array String data-seq) el)) 
      :else (throw (IllegalArgumentException. (str "Don't know how to parse " (class el)))))))

(defn map-to-RList [data]
  (org.rosuda.REngine.RList. 
    (map to-REXPVector (vals data)) (into-array String (keys data))))

(defn RList-as-dataframe [RList] 
  (org.rosuda.REngine.REXP/createDataFrame RList))
