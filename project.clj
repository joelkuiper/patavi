(defproject clinicico "0.2.2"
  :description "A work-in-progress wrapper to create a RESTful webservice from an R script"
  :url "http://clinici.co"
  :repositories {"local" ~(str (.toURI (java.io.File. "third-party/repo")))}
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [compojure "1.1.5"]
                 [ring-middleware-format "0.2.3"]
                 [ring/ring-json "0.1.2"]
                 [org.clojure/tools.logging "0.2.4"]
                 [log4j/log4j "1.2.17"]
                 [com.google.guava/guava "13.0.1"]
                 [com.novemberain/monger "1.4.2"]
                 [joda-time/joda-time "2.1"]
                 [local/RserveEngine "1.7.0"]
                 [local/REngine "1.7.0"]]
  :plugins [[lein-ring "0.8.2"]
            [lein-marginalia "0.7.1"]]
  :aot [clinicico.ResourceNotFound]
  :ring {:handler clinicico.handler/app
         :init clinicico.handler/main}
  :profiles
  {:dev {:dependencies [[lein-marginalia "0.8.0-SNAPSHOT"]
                        [ring-mock "0.1.3"]]}})
