(ns clinicico.worker.pirate.core
  (:use [clojure.java.shell :only [sh]]
        [nio2.watch]
        [nio2.files]
        [clojure.string :only [split join]])
  (:require [clojure.java.io :as io]
            [clinicico.worker.pirate.util :as pirate]
            [clojure.tools.logging :as log]
            [nio2.io :as io2]
            [cheshire.core :refer :all :as json])
  (:import (org.rosuda.REngine REngineException)
           (org.rosuda.REngine.Rserve RConnection)))

(def ^:private default-packages ["RJSONIO" "Rserve"])

(def ^:private load-template
  (str "l = tryCatch(require('%1$s'), warning=function(w) w);
        if(is(l, 'warning')) print(l[1])"))

(def ^:private bootstrap-template "#AUTO-GENERATED\nsource('%s')\n")

(defn- create-bootstrap
  [extra-packages]
  (let [packages (concat extra-packages default-packages)
        commands (map #(format load-template %) packages)
        wrapper (io/as-relative-path "resources/wrap.R")
        bootstrap (str (format bootstrap-template wrapper) (join "\n" commands))]
    (spit (io/resource "bootstrap.R") bootstrap)))

(defn initialize
  [file packages]
  (create-bootstrap packages)
  (sh (io/as-relative-path "scripts/start.sh")))

(defn- load-file!
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

(defn- watch-progress
  "Reads the changes from the progress file and fires the callback"
  [file callback]
  (let [p (io2/path file)]
    (log/debug "Start watching" file " with " (real-path p))
    (with-open [rdr (io/reader p)]
      (log/debug "Reading files")
      (doseq [e (watch-seq (parent (real-path p)) :modify)]
        (log/debug (real-path (:path e)) " " (real-path p))
        ))))

(defn execute
  [file method params]
  (with-open [R (pirate/connect)]
    (try
      (load-file! R file)
      (pirate/assign R "params" (json/encode params))
      (pirate/assign R "id" (:id params))
      (let [progress-file (str (:id params) ".tmp")
            workdir (pirate/parse R "getwd()")
            path (str workdir "/" progress-file)]
        (do
          (pirate/create-file! R progress-file)
          (let [watch-thread (Thread. (watch-progress path (fn [msg] (log/debug msg))))]
            (.start watch-thread)
            (json/decode (pirate/parse R (str "exec(" method ", params)")))
            (.join watch-thread))))
      (catch Exception e (throw (Exception. (cause e) e))))))
