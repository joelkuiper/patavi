(defproject clinicico.worker "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :repositories {"local" ~(str (.toURI (java.io.File. "third-party/repo")))}
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/tools.cli "0.2.2"]
                 [clinicico.common "0.1.0-SNAPSHOT"]
                 [crypto-random "1.1.0"]
                 [cheshire "5.2.0"]
                 [local/RserveEngine "1.7.0"]
                 [local/REngine "1.7.0"]]
  :main clinicico.worker.main)
