(ns clinicico.config
  (:use clojure.java.io))

(defn load-properties
  "Reads a java properties file and parses it into a map."
  [file-name]
  (with-open [^java.io.Reader reader (reader file-name)] 
    (let [props (java.util.Properties.)]
      (.load props reader)
      (into {} (for [[k v] props] [(keyword k) (read-string v)])))))

(def ^:dynamic *config* (load-properties (resource "config.properties")))

(def base-url (str 
                (:scheme *config*) "://"  
                (:server-name *config*) ":" 
                (:server-port *config*) "/")) 
