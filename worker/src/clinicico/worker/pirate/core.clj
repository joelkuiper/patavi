(ns clinicico.worker.pirate.core
  (:require [clojure.java.io :as io]
            [clojure.core.async :as async :refer :all]
            [clojure.string :refer [split join]]
            [clojure.java.shell :refer [sh]]
            [clinicico.worker.pirate.util :as pirate]
            [clojure.tools.logging :as log]
            [zeromq.zmq :as zmq]
            [cheshire.core :as json :only [decode]]
            [crypto.random :as crypto])
  (:import (org.rosuda.REngine REngineException)
           (org.rosuda.REngine.Rserve RConnection)))

(def ^:private script-file (atom nil))

(def ^:private default-packages ["Rserve" "RJSONIO" "Cairo"])

(def ^:private load-template
  (str "l = tryCatch(require('%1$s'), warning=function(w) w);"
       "if(is(l, 'warning')) print(l[1])"))

(def ^:private bootstrap-template "#AUTO-GENERATED\nsource('%s')\n")

(defn- create-bootstrap
  [extra-packages]
  (let [packages (concat extra-packages default-packages)
        commands (map #(format load-template %) packages)
        wrapper (io/as-relative-path "resources/wrap.R")]
    (str (format bootstrap-template wrapper) (join "\n" commands))))

(defn initialize
  "Generates a bootstrap.R file and executes scripts/start.sh in a shell
   Typically starting a new RServe with the generated file 'sourced'"
  [file packages start?]
  (do
    (reset! script-file (io/as-file file))
    (when start?
      (create-bootstrap packages)
      (let [start (sh (io/as-relative-path "scripts/start.sh"))]
        (log/info "[Rserve]" (:out start))
        start))))

(defn- source-script!
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
  (with-open [R (pirate/connect callback)]
    (try
      (do
        (source-script! R @script-file)
        (pirate/assign R "params" params)
        (pirate/assign R "files" [])
        (let [call (format "exec(%s, params)" method)
              result (pirate/parse R call)]
          {:id id
           :method method
           :files (pirate/retrieve R "files")
           :results (json/decode result)}))
      (catch Exception e (throw (Exception. (cause e) e))))))
