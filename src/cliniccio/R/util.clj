(ns clinicico.R.util
  (:use     [clinicico.util]) 
  (:require [clojure.java.io :as io])
  (:import (org.rosuda.REngine)
           (org.rosuda.REngine REXP RList)
           (org.rosuda.REngine REXPDouble REXPLogical REXPFactor REXPInteger REXPString REXPGenericVector)
           (org.rosuda.REngine.Rserve RConnection)))

(defn connect [] 
  (let [conn (try 
               (RConnection.)
               (catch Exception e (throw (Exception. "Could not connect to RServe" e))))]
    (if (.isConnected conn) 
      conn)))

(defn- convert-fn [field] 
  (cond 
    (instance? REXPFactor field) (comp #(.asStrings %) #(.asFactor ^REXPFactor %))
    (instance? REXPInteger field) #(.asIntegers ^REXPInteger %)
    (instance? REXPDouble field) #(.asDoubles ^REXPDouble %)
    :else (throw (Exception. (str "Could not convert field " field)))))

(defn as-list 
  ([data] (.asList ^REXPGenericVector data))
  ([data $field] (as-list (.at ^RList data $field))))

(defn in-list  [data $field]
  (let [members (.at ^RList data $field)]
    (if-not (nil? members)
      (seq ((convert-fn members) members))
      nil)))

(defn parse [^RConnection R cmd] 
  (.parseAndEval R cmd nil true))

(defn plot [^RConnection R item] 
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
  (let [ks (.keys ^RList data)] 
    (zipmap ks 
            (map (fn [k] 
                   (let [field (.at ^RList data k)]
                     (seq ((convert-fn field) field)))) ks))))

(defn- factor-indexes [lst] 
  (let [levels (sort (distinct lst))]
    (map #(inc (.indexOf levels %)) lst)))

(defn to-REXPVector
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
  (RList. 
    (map to-REXPVector (vals data)) (into-array String (keys data))))

(defn RList-as-dataframe [RList] 
  (REXP/createDataFrame RList))
