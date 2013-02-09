;; ## R 
;; R interoperability is provided by [Rserve](http://www.rforge.net/Rserve/). 
;; RServe provides both a TCP/IP server for calling R functionality as well as
;; a Java library for convenient access to an Rserve.  
;;
;; The functions below are mostly utilities for converting back and forth
;; between native Clojure representations (maps and vectors) and RServes'
;; classes.  

(ns clinicico.R.util
  (:use     [clinicico.util.util] 
            [clinicico.config])
  (:require [clojure.java.io :as io])
  (:import (org.rosuda.REngine)
           (org.rosuda.REngine REXP RList)
           (org.rosuda.REngine REXPDouble REXPLogical 
                               REXPFactor REXPInteger 
                               REXPString REXPGenericVector
                               REXPNull REngineException)
           (org.rosuda.REngine.Rserve RConnection)))


(defn connect 
  "Connect to an RServe instance. Each connection creates its own workspace
   and forks the existing R process, effectively sandboxing the R operations.

   Returns an [RConnection](http://rforge.net/org/doc/org/rosuda/REngine/Rserve/RConnection.html) 
   which is referred to as R in the code."
  [] 
  (let [conn (try 
               (RConnection. 
                 (str (:rserve-host *config* "localhost")) 
                 (Integer. (:rserve-port *config* 6311)))
               (catch Exception e (throw (Exception. "Could not connect to RServe" e))))]
    (if (.isConnected conn) 
      conn)))

(defn as-list 
  "Converts a 
   [REXPGenericVector](http://rforge.net/org/doc/org/rosuda/REngine/REXPGenericVector.html), 
   or a nested list, to an [RList](http://rforge.net/org/doc/org/rosuda/REngine/RList.html)."
  ([data] (.asList ^REXPGenericVector data))
  ([data $field] (as-list (.at ^RList data $field))))

(defn convert-fn 
  "Conditionally converts a 
   [REXPVector](http://rforge.net/org/doc/org/rosuda/REngine/REXPVector.html) to primitives."
  [field] 
  (cond  ;; REXPFactor is-a REXPInteger, so the order matters
        (.isNull field) (fn [x] nil)
        (instance? REXPFactor field) (comp #(.asStrings %) #(.asFactor ^REXPFactor %))
        (instance? REXPString field) #(.asStrings ^REXPString %)
        (instance? REXPInteger field) #(.asIntegers ^REXPInteger %)
        (instance? REXPDouble field) #(.asDoubles ^REXPDouble %)
        (instance? REXPLogical field) #(.isTrue ^REXPLogical %) 
        :else (throw (Exception. (.toString (class field))))))

(defn parse-matrix 
  "Parses a (named) matrix (2d-list in R) to a map 
   with the rows as a map between labels and values.
   If not a named matrix it will substitute the missing names 
   with an incrementing index starting with 1." 
  [matrix] 
  (let [dimnames (.getAttribute matrix "dimnames")
        labels  (map 
                  (fn [ls] 
                    (if-not (.isNull ls) 
                      (seq (.asStrings ls)) 
                      nil)) 
                  (.asList dimnames))
        data (map seq (.asDoubleMatrix matrix))]
    (into {} 
          (mapcat (fn [[k v]] 
                    {k (zipmap (or (second labels) (range 1 (inc (count v)))) v)}) 
                  (zipmap (or (first labels) (range 1 (inc (count data)))) data)))))

(def into-map
  "Recursively transforms a (nested) named RList into a map"
  (fn [data]
    (let [ks (.keys ^RList data)] 
      (zipmap ks 
              (map (fn [k] 
                     (let [field (.at ^RList data k)]
                       (if (instance? REXPGenericVector field)
                         (into-map (as-list field))
                         (seq ((convert-fn field) field))))) ks)))))

(defn into-clj
 "Converts the given REXP to a Clojure representation" 
  [rexp]
  (if (instance? REXPGenericVector rexp) 
    (into-map rexp)
    (let [array ((convert-fn rexp) rexp)]
      (if (> (count array) 1) (seq array) (first array)))))

(defn in-list 
  "Returns a field in a list as Clojure sequence (seq) using the `convert-fn`.
   Rough equivalent to R's data$field accessor." 
  [data $field]
  (let [members (as-list data $field)]
    (if-not (nil? members)
      (into-clj members)
      nil)))

(defn parse 
  "Evaluates and parses the R expression cmd, and 
   throws an REngineException if the evaluation was unsuccesful.
   Takes an optional parameter convert? indicating to convert the returned REXP"
  ([^RConnection R cmd]
   (parse R cmd true))
  ([^RConnection R cmd convert?] 
   (let [trycmd (str "try("cmd", silent=T)")
         evaluation (.parseAndEval R trycmd nil true)]
     (if (.inherits evaluation "try-error")
       (throw (REngineException. R (.asString evaluation)))
       (if convert? (into-clj evaluation) evaluation)))))

(defn- factor-indexes 
  [lst] 
  (let [levels (sort (distinct lst))]
    (map #(inc (.indexOf levels %)) lst)))

(defn into-rexp-vector
  "Converts a sequential or a primitive to a 
   subclass of [REXPVector](http://rforge.net/org/doc/org/rosuda/REngine/REXPVector.html)."
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
      (REXPFactor. (int-array (factor-indexes data-seq)) 
                   (into-array String (sort (distinct data-seq)))) 
      (instance? String el) 
      (REXPString. (if is-seq (into-array String data-seq) el)) 
      :else (throw (IllegalArgumentException. (str "Cannot parse " (class el)))))))

(defn into-r-list  
  "Converts a map to an RList, using the to-REXPVector 
   to convert underlying elements to REXPVectors."
  [data]
  (RList. 
    (map into-rexp-vector (vals data)) (into-array String (keys data))))

(defn r-list-as-dataframe [RList] 
  (REXP/createDataFrame RList))
