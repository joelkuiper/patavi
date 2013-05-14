(ns clinicico.worker.pirate.core
  (:use [clojure.java.shell :only [sh]]
        [clojure.string :only [split join]])
  (:require [clojure.java.io :as io]
            [clinicico.worker.pirate.util :as pirate]
            [clojure.tools.logging :as log]
            [cheshire.core :refer :all :as json])
  (:import (org.rosuda.REngine)
           (org.rosuda.REngine.Rserve RConnection)))

(def ^:private default-packages ["RJSONIO" "Cairo"])

(def ^:private load-template (str "l = tryCatch(require('%1$s'), warning=function(w) w);
                         if(is(l, 'warning')) print(l[1])"))

(def ^:private bootstrap-template "#AUTO-GENERATED \nsource('%s')\n")

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

(defn execute
  [file method params]
  (with-open [R (pirate/connect)]
    (load-file! R file)
    (pirate/assign R "params" (json/encode params))
    (json/decode (pirate/parse R (str "exec(" method ", params)")))))
