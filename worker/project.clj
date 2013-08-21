(defproject clinicico.worker "0.1.0-SNAPSHOT"
  :url "http://example.com/FIXME"
  :description ""
  :repositories {"drugis" "http://drugis.org/mvn"}
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [clinicico.common "0.1.0-SNAPSHOT"]
                 [cheshire "5.2.0"]
                 [org.rosuda/REngine "1.7.1-SNAPSHOT"]]
  :main clinicico.worker.main)
