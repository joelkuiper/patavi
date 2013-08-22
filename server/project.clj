(defproject clinicico.server "0.1.0-SNAPSHOT"
  :description "Clinici.co is a distributed system for exposing R as RESTful web services"
  :license {:name "The MIT License"
            :url "http://opensource.org/licenses/MIT"
            :distribution :repo}
  :url "http://clinici.co"
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [compojure "1.1.5"]
                 [clinicico.common "0.1.0-SNAPSHOT"]
                 [ring/ring-devel "1.2.0"]
                 [ring.middleware.jsonp "0.1.3"]
                 [http-kit "2.1.9"]
                 [cheshire "5.2.0"]
                 [overtone/at-at "1.2.0"]
                 [liberator "0.9.0"]
                 [org.clojure/tools.nrepl "0.2.3"]
                 [inflections "0.8.1"]
                 [org.clojure/data.xml "0.0.7"]
                 [com.novemberain/monger "1.6.0"]]
  :profiles {:dev {:resource-paths ["resources-dev"]
                   :dependencies [[org.clojure/tools.namespace "0.2.4"]]}
             :production {:resource-paths ["resources-prod"]
                          :jvm-opts ["-Xmx1g" "-server"]}}
  :main clinicico.server.server)
