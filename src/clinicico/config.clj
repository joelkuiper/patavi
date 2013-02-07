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

(def api-url (str (:scheme *config*) "://"
                  (if (:api-subdomain *config*) 
                    (str (:api-subdomain *config*) "."))
                  (:server-name *config*)
                  (if-not (= (:server-port *config*) 80)
                    (str ":" (:server-port *config*)))
                  (if-not (:api-subdomain *config*)
                   "/api") 
                  "/"))

(def base-url (str 
                (:scheme *config*) "://"  
                (:server-name *config*)
                (if-not (= (:server-port *config*) 80)
                  (str ":" (:server-port *config*)))
                "/"))
