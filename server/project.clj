(defproject clinicico.server "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [compojure "1.1.5"]
                 [cheshire "5.1.1"]
                 [liberator "0.8.0"]
                 [org.clojure/tools.logging "0.2.4"]
                 [log4j/log4j "1.2.17"]
                 [inflections "0.8.1"]
                 [clj-time "0.5.0"]
                 [org.clojure/data.xml "0.0.7"]
                 [com.novemberain/langohr "1.0.0-beta13"]]
  :plugins [[lein-ring "0.8.3"]]
  :ring {:handler clinicico.server.handler/app
         :init clinicico.server.handler/main}
  :profiles
  {:dev {:dependencies [[ring-mock "0.1.3"]]}})
