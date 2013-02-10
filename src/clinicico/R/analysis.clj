(ns clinicico.R.analysis
  (:use validateur.validation
        clojure.walk
        clinicico.util.util)
  (:require [clinicico.R.util :as R]
            [clojure.java.io :as io]))

(def validators (atom {}))

(defn copy-to-r 
  [R file filename]
  (with-open [r-file (.createFile R filename)] 
    (io/copy file r-file)))

(defn- upload-file-parameter
  [R file]
  (copy-to-r (get file "tempfile") (key file)))

(defn load-analysis! 
  "Finds the R file with the associated analysis 
   name and load its into an RConnection."
  [R analysis]
  (let [script (io/as-file (io/resource (str "R/" analysis ".R")))]
    (if (nil? script)
      (throw (IllegalArgumentException. (str "Could not find specified analysis " analysis)))
      (do
        (copy-to-r R script analysis) 
        (.voidEval R (str "source('"script"')"))
        (.removeFile R analysis)))))

(defn dispatch 
  [analysis params]
  (if (not (valid? (get @validators analysis (validation-set)) params))
    (throw (IllegalArgumentException. (str "Provided parameters were not valid")))
    (let [files (select-keys params (for [[k v] params :when (contains? v :file)] k)) ; These are just the files to be copied
          options (into {} (map (fn [[k v]] ; We replace the value of each of the files as a map to a their filename
                                  (if (contains? v :file) 
                                    [k {"file" (get-in v [:file :filename])}] 
                                    [k v])) params))]
      (with-open [R (R/connect)]
        (load-analysis! R analysis)
        (map #(upload-file-parameter R %) files)
        (R/assign R "params" options)
        (R/into-clj (R/parse R (str analysis "(params)") false))))))
