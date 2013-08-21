;; ## R
;; R interoperability is provided by [Rserve](http://www.rforge.net/Rserve/).
;; RServe provides both a TCP/IP server for calling R functionality as well as
;; a Java library for convenient access to an Rserve.
;;
;; The functions below are mostly utilities for converting back and forth
;; between native Clojure representations (maps and vectors) and RServes'
;; classes.

(ns clinicico.worker.pirate.util
  (:require [clojure.java.io :as io])
  (:import (com.google.common.primitives Ints)
           (org.rosuda.REngine)
           (org.rosuda.REngine REXP RList
                               REXPDouble REXPLogical
                               REXPFactor REXPInteger
                               REXPString REXPGenericVector
                               REXPNull REngineException
                               REXPRaw REXPSymbol
                               REXPList)
           (org.rosuda.REngine.Rserve RConnection)))

;(set! *warn-on-reflection* true)


(defn create-file!
  [^RConnection R filename]
  (.createFile R filename))

(defn copy!
  [^RConnection R file filename]
  (with-open [r-file (create-file! R filename)]
    (io/copy (slurp file) r-file)))

(defn- as-list
  "Converts a
   [REXPGenericVector](http://rforge.net/org/doc/org/rosuda/REngine/REXPGenericVector.html),
   to an [RList](http://rforge.net/org/doc/org/rosuda/REngine/RList.html)."
  ([data] (.asList ^REXPGenericVector data)))

(defn- convert-fn
  "Conditionally converts a
   [REXPVector](http://rforge.net/org/doc/org/rosuda/REngine/REXPVector.html) to primitives."
  [field]
  (cond  ;; REXPFactor is-a REXPInteger, so the order matters
        (.isNull field) (fn [_] nil)
        (instance? REXPFactor field) (comp #(.asStrings %) #(.asFactor ^REXPFactor %))
        (instance? REXPString field) #(.asStrings ^REXPString %)
        (instance? REXPInteger field) #(.asIntegers ^REXPInteger %)
        (instance? REXPDouble field) #(.asDoubles ^REXPDouble %)
        (instance? REXPLogical field) #(.isTrue ^REXPLogical %)
        (instance? REXPRaw field) #(.asBytes ^REXPRaw %)
        :else (throw (Exception. (str (class field))))))

(defn- first-or-seq
  [array]
  (if (> (count array) 1) (seq array) (first array)))

(def into-map
  "Recursively transforms a (nested) named RList into a map"
  (fn [data]
    (let [values (map (fn [field]
                        (if (instance? REXPGenericVector field)
                          (into-map (as-list field))
                          (first-or-seq ((convert-fn field) field)))) data)]
      (if (.isNamed ^RList data)
        (zipmap (.keys ^RList data) values)
        (vec values)))))

(defn into-clj
  "Converts the given REXP to a Clojure representation"
  [rexp]
  (cond (instance? RList rexp) (into-map rexp)
        (instance? REXPGenericVector rexp) (into-clj (as-list rexp))
        :else (first-or-seq ((convert-fn rexp) rexp))))

(defn parse
  "Evaluates and parses the R expression cmd, and
   throws an REngineException if the evaluation was unsuccesful.
   Takes an optional parameter convert? indicating whether or not to
   convert the returned REXP to a Clojure representation"
  ([^RConnection R cmd]
   (parse R cmd true))
  ([^RConnection R cmd convert?]
   (let [trycmd (str "try("cmd", silent=T)")
         evaluation (.parseAndEval ^RConnection R trycmd nil true)]
     (if (.inherits evaluation "try-error")
       (throw (REngineException. ^RConnection R (.asString evaluation)))
       (if convert? (into-clj evaluation) evaluation)))))

(defn- factor-indexes
  [lst]
  (let [levels (sort (distinct lst))]
    (map #(inc (.indexOf levels %)) lst)))

(defn- cast-long
  [number]
  (Ints/checkedCast number))

(defn into-r
  "Converts Clojure data-structures into
   subclass of [REXPVector](http://rforge.net/org/doc/org/rosuda/REngine/REXPVector.html).
   REngine does not recognize longs so it will attempt to cast them to integers
   Currently does not handle Raw data"
  [data-seq]
  (if (and (counted? data-seq) (every? (or associative? sequential?) data-seq))
    (REXPGenericVector.
      (if (map? data-seq)
        (RList. (map into-r (vals data-seq))
                (into-array String (map #(if (keyword? %) (name %) (str %)) (keys data-seq))))
        (RList. (map into-r data-seq))))
    (let [is-seq (sequential? data-seq)
          el (if is-seq (first data-seq) data-seq)]
      (cond
        (nil? el) (REXPNull.)
        (instance? Integer el)
        (REXPInteger. (if is-seq (int-array data-seq) (int el)))
        (instance? Long el)
        (REXPInteger. (if is-seq (int-array (map cast-long data-seq)) (cast-long el)))
        (instance? Boolean el)
        (REXPLogical. (if is-seq (boolean-array data-seq) (boolean el)))
        (instance? Number el)
        (REXPDouble. (if is-seq (double-array data-seq) (double el)))
        (and (instance? String el) is-seq (every? #(re-matches #"\w*" %) data-seq))
        (REXPFactor. (int-array (factor-indexes data-seq))
                     (into-array String (sort (distinct data-seq))))
        (instance? String el)
        (REXPString. (if is-seq (into-array String data-seq) el))
        :else (throw
                (IllegalArgumentException.
                  (str "Error parsing" (class el) " " data-seq)))))))

(defn assign
  "Assigns the given value as converted by into-r
   into an RConnection with a given name"
  [^RConnection R varname m]
  (.assign R varname ^REXP (into-r m)))

(defn retrieve
  [^RConnection R varname]
  (into-clj (.get R varname nil true)))

(defn connect
  "Connect to an RServe instance. Each connection creates its own workspace
   and forks the existing R process, effectively sandboxing the R operations.

   Returns an [RConnection](http://rforge.net/org/doc/org/rosuda/REngine/Rserve/RConnection.html)
   which is referred to as R in the code."
  ([callback] (connect "localhost" 6311 callback))
  ([host port callback]
  (let [wrapped-callback (proxy [org.rosuda.REngine.Rserve.RConnection$OutOfBandCallback] []
                           (update [msg] (callback (into-clj msg))))
        ^RConnection conn (try
               (RConnection. host (Integer. port))
               (catch Exception e (throw (Exception. "Could not connect to RServe" e))))]
    (when (.isConnected conn)
      (.addOutOfBandCallback conn wrapped-callback)
      conn))))
