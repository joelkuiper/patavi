(ns clinicico.worker.pirate.core
  (:use [clojure.java.shell :only [sh]]
        [clojure.string :only [split join]])
  (:require [clojure.java.io :as io]
            [clinicico.worker.pirate.util :as pirate]
            [clojure.tools.logging :as log]
            [zeromq.zmq :as zmq]
            [cheshire.core :as json :only [decode]]
            [crypto.random :as crypto])
  (:import (org.rosuda.REngine REngineException)
           (org.rosuda.REngine.Rserve RConnection)))

(def script-file (atom nil))

(def ^:private default-packages ["RJSONIO" "rzmq" "Cairo"])

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
  [file packages start?]
  (do
    (reset! script-file (io/as-file file))
    (when start?
      (create-bootstrap packages)
      (let [start (sh (io/as-relative-path "scripts/start.sh"))]
        (log/info "[Rserve]" (:out start))
        start))))

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

(defn- create-listener
  [context socket callback]
  (let [poller (zmq/poller context)]
    (fn []
      (zmq/register poller socket :pollin)
      (while (not (.. Thread currentThread isInterrupted))
        (.poll poller)
        (when (.pollin poller 0)
          (callback (zmq/receive-str socket)))))))

(defn listen-for-updates
  [callback]
  (let [context (zmq/context)
        port (zmq/first-free-port)
        socket (zmq/socket context :sub)
        listener (Thread. (create-listener context socket callback))]
    (zmq/bind (zmq/subscribe socket  "") (str "tcp://*:" port))
    (.start listener)
    {:socket socket
     :port port
     :close (fn []
              (do (.interrupt listener) (.close socket) (.join listener 10)))}))

(defn execute
  "Executes, in R, the method present in the file with the given params.
   Callback is function taking one argument which can serve to
   allow OOB updates from the R session
   See resources/wrap.R for details."
  [method id params callback]
  (with-open [R (pirate/connect)]
    (let [updates (listen-for-updates callback)]
      (try
        (do
          (source-file! R @script-file)
          (pirate/assign R "params" params)
          (pirate/assign R "files" [])
          (let [call (format "exec(%s, '%s', params)" method (:port updates))
                result (pirate/parse R call)]
            {:id id
             :method method
             :files (pirate/retrieve R "files")
             :results (json/decode result)}))
        (catch Exception e (throw (Exception. (cause e) e)))
        (finally ((:close updates)))))))
