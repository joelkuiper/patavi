(ns clinicico.worker.pirate.core
  (:use [clojure.java.shell :only [sh]]
        [clojure.string :only [split join]])
  (:require [clojure.java.io :as io]
            [clinicico.worker.pirate.util :as pirate]
            [clinicico.worker.util.nio :as nio]
            [clojure.tools.logging :as log]
            [cheshire.core :refer :all :as json]
            [crypto.random :as crypto]
            [nio.core :as nio2 :only [mmap]])
  (:import (org.rosuda.REngine REngineException)
           (org.rosuda.REngine.Rserve RConnection)))

(def ^:private default-packages ["RJSONIO" "Cairo"])

(def ^:private load-template
  (str "l = tryCatch(require('%1$s'), warning=function(w) w);"
       "if(is(l, 'warning')) print(l[1])"))

(def ^:private bootstrap-template "#AUTO-GENERATED\nsource('%s')\n")

(defn- create-bootstrap
  [extra-packages]
  (let [packages (concat extra-packages default-packages)
        commands (map #(format load-template %) packages)
        wrapper (io/as-relative-path "resources/wrap.R")
        bootstrap (str (format bootstrap-template wrapper) (join "\n" commands))]
    (spit (io/resource "bootstrap.R") bootstrap)))

(def script-file (atom ""))
(defn initialize
  "Generates a bootstrap.R file and executes scripts/start.sh in a shell
   Typically starting a new RServe with the generated file 'sourced'"
  [file packages]
  (reset! script-file (nio2/mmap file))
  (create-bootstrap packages)
  (let [start (sh (io/as-relative-path "scripts/start.sh"))]
    (log/info "[Rserve]" (:out start))
    start))

(defn- source-file!
  "Finds the R file with the associated file
   name and load its into an RConnection."
  [^RConnection R script]
  (let [filename (crypto.random/hex 8)]
    (if (nil? script)
      (throw (IllegalArgumentException.
               (str "Could not source script file to R")))
      (do
        (pirate/copy! R script filename)
        (.voidEval R (str "source('"filename"')"))
        (.removeFile R filename)))))

(defn- cause
  [^Exception e]
  (let [cause (.getCause e)]
    (if (and (not (nil? e)) (instance? REngineException cause))
      (.getMessage cause)
      (str e))))

(defn execute
  "Executes, in R, the method present in the file with the given params.
   Callback is function taking one argument which can serve to
   allow OOB updates from the R session
   See resources/wrap.R for details."
  [method id params callback]
  (with-open [R (pirate/connect)]
    (try
      (source-file! R @script-file)
      (pirate/assign R "params" params)
      (pirate/assign R "files" [])
      (let [progress-file (str id ".tmp")
            workdir (pirate/parse R "getwd()")
            path (str workdir "/" progress-file)]
        (do
          (pirate/create-file! R progress-file)
          (nio/tail-file path :modify callback)
          (let [result (pirate/parse R (format "exec(%s, '%s', params)" method id))
                files (pirate/retrieve R "files")]
            (nio/unwatch-file path)
            {:files (if (map? files) [files] files)
             :results (json/decode result)})))
      (catch Exception e (throw (Exception. (cause e) e))))))
