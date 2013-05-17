(ns clinicico.worker.pirate.core
  (:use [clojure.java.shell :only [sh]]
        [clojure.string :only [split join]])
  (:require [clojure.java.io :as io]
            [clinicico.worker.pirate.util :as pirate]
            [clinicico.worker.util.nio :as nio]
            [clojure.tools.logging :as log]
            [cheshire.core :refer :all :as json])
  (:import (org.rosuda.REngine REngineException)
           (org.rosuda.REngine.Rserve RConnection)))

(def ^:private default-packages ["RJSONIO"])

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

(defn initialize
  "Generates a bootstrap.R file and executes scripts/start.sh in a shell
   Typically starting a new RServe with the generated file 'sourced'"
  [file packages]
  (create-bootstrap packages)
  (sh (io/as-relative-path "scripts/start.sh")))

(defn- source-file!
  "Finds the R file with the associated file
   name and load its into an RConnection."
  [^RConnection R file]
  (let [script (io/as-file file)
        filename (.getName script)]
    (if (nil? script)
      (throw (IllegalArgumentException.
               (str "Could not find specified file " file)))
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
  [file method id params callback]
  (with-open [R (pirate/connect)]
    (try
      (source-file! R file)
      (pirate/assign R "params" params)
      (let [progress-file (str id ".tmp")
            workdir (pirate/parse R "getwd()")
            path (str workdir "/" progress-file)]
        (do
          (pirate/create-file! R progress-file)
          (nio/tail-file path :modify callback)
          (let [result (pirate/parse R (format "exec(%s, '%s', params)" method id))]
            (nio/unwatch-file path)
            (json/decode result))))
      (catch Exception e (throw (Exception. (cause e) e))))))
