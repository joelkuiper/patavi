;; From http://stackoverflow.com/questions/7777882/loading-configuration-file-in-clojure-as-data-structure
(ns patavi.worker.config
  (:require [clojure.java.io :refer :all]))

(defn- load-props
  [file-name]
  (with-open [^java.io.Reader reader (reader file-name)]
    (let [props (java.util.Properties.)]
      (.load props reader)
      (into {} (for [[k v] props] [(keyword k) (read-string v)])))))

(def config (load-props (resource "config.properties")))
