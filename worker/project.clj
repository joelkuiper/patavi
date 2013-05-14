(defproject clinicico.worker "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :repositories {"local" ~(str (.toURI (java.io.File. "third-party/repo")))}
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/tools.cli "0.2.2"]
                 [org.clojure/tools.logging "0.2.4"]
                 [log4j/log4j "1.2.17"]
                 [cheshire "5.1.1"]
                 [local/RserveEngine "1.7.0"]
                 [local/REngine "1.7.0"]
                 [com.novemberain/langohr "1.0.0-beta13"]]
  :main clinicico.worker.main
  :profiles
  {:dev {:dependencies [[lein-marginalia "0.7.1"]
                        [ring-mock "0.1.3"]]}})
