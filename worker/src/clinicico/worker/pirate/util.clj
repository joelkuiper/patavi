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
  "Creates a file in R"
  [^RConnection R filename]
  (.createFile R filename))

(defn copy!
  "Copies (using io/copy) the file into the file with filename"
  [^RConnection R ^java.io.Closeable file filename]
  (with-open [^java.io.Closeable r-file (create-file! R filename)]
    (io/copy (slurp file) r-file)))

(defn- as-list
  "Converts a
   [REXPGenericVector](http://rforge.net/org/doc/org/rosuda/REngine/REXPGenericVector.html),
   to an [RList](http://rforge.net/org/doc/org/rosuda/REngine/RList.html)."
  ([data] (.asList ^REXPGenericVector data)))

(defmulti convert-from (fn [field] [(type field)]))
(defmethod convert-from [nil] [_] nil)
(defmethod convert-from [REXPFactor] [field] (.asStrings ^REXPFactor field))
(defmethod convert-from [REXPString] [field] (.asStrings ^REXPString field))
(defmethod convert-from [REXPInteger] [field] (.asIntegers ^REXPInteger field))
(defmethod convert-from [REXPDouble] [field] (.asDoubles ^REXPDouble field))
(defmethod convert-from [REXPLogical] [field] (.isTrue ^REXPLogical field))
(defmethod convert-from [REXPRaw] [field] (.asBytes ^REXPRaw field))

(defn- first-or-seq
  [array]
  (if (> (count array) 1) (seq array) (first array)))

(def into-map
  "Recursively transforms a (nested) named RList into a map"
  (fn [data]
    (let [values (map (fn [field]
                        (if (instance? REXPGenericVector field)
                          (into-map (as-list field))
                          (first-or-seq (convert-from field)))) data)]
      (if (.isNamed ^RList data)
        (zipmap (.keys ^RList data) values)
        (vec values)))))

(defn into-clj
  "Converts the given REXP to a Clojure representation"
  [^REXP rexp]
  (cond (instance? RList rexp) (into-map rexp)
        (instance? REXPGenericVector rexp) (into-clj (as-list rexp))
        :else (first-or-seq (convert-from rexp))))

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

(defmulti convert-to (fn [field]
                       (if (sequential? field)
                         [(type (first field)) true]
                         [(type field) false])))
(defmethod convert-to [nil] [_ _] (REXPNull.))
(defmethod convert-to [Integer false] [field]
  (REXPInteger. (int field)))
(defmethod convert-to [Integer true] [field]
  (REXPInteger. (int-array field)))
(defmethod convert-to [Long false] [field]
  (REXPInteger. (cast-long field)))
(defmethod convert-to [Long true] [field]
  (REXPInteger. (int-array (map cast-long field))))
(defmethod convert-to [Boolean true] [field]
  (REXPLogical. (boolean-array field)))
(defmethod convert-to [Boolean false] [field]
  (REXPLogical. (boolean field)))
(defmethod convert-to [Double true] [field]
  (REXPDouble. (double-array field)))
(defmethod convert-to [Double false] [field]
  (REXPDouble. (double field)))
(defmethod convert-to [String true] [field]
  (if (every? #(re-matches #"\w*" %) field)
    (REXPFactor. (int-array (factor-indexes field))
                 (into-array String (sort (distinct field))))
    (REXPString. (into-array String field))))
(defmethod convert-to [String false] [field]
  (REXPString. field))

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
    (convert-to data-seq)))

(defn assign
  "Assigns the given value as converted by into-r
   into an RConnection with a given name"
  [^RConnection R varname m]
  (.assign R ^String varname ^REXP (into-r m)))

(defn retrieve
  [^RConnection R varname]
  (into-clj (.get R varname nil true)))

(defn connect
  "Connect to an RServe instance. Each connection creates its own workspace
   and forks the existing R process, effectively sandboxing the R operations.

   Returns an [RConnection](http://rforge.net/org/doc/org/rosuda/REngine/Rserve/RConnection.html)
   which is referred to as R in the code."
  ([] (connect #()))
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
