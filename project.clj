(defproject cliniccio "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :repositories {"local" ~(str (.toURI (java.io.File. "third-party/repo")))}
  :dependencies [[org.clojure/clojure "1.4.0"]
                
                 [compojure "1.1.5"]
                 [ring-middleware-format "0.2.3"]
                 
                 [org.clojure/tools.logging "0.2.4"]
                 [log4j/log4j "1.2.17"]
                 
                 [lamina "0.5.0-beta9"]
                 [local/RserveEngine "1.7.0"]
                 [local/REngine "1.7.0"]]
  :plugins [[lein-ring "0.8.2"]]

  :ring {:handler cliniccio.handler/app
         :init cliniccio.handler/main}
  :profiles
  {:dev {:dependencies [[ring-mock "0.1.3"]]}})
