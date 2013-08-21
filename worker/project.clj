(defproject clinicico.worker "0.1.0-SNAPSHOT"
  :url "http://clinici.co"
  :license {:name "The MIT License"
            :url "http://opensource.org/licenses/MIT"
            :distribution :repo}
  :description "Workers listen for tasks and dispatch them to RServe"
  :repositories {"drugis" "http://drugis.org/mvn"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [clinicico.common "0.1.0-SNAPSHOT"]
                 [cheshire "5.2.0"]
                 [org.rosuda/REngine "1.7.1-SNAPSHOT"]]
  :profiles {:dev {:resource-paths ["resources-dev" "resources"]
                   :dependencies [[org.clojure/tools.namespace "0.2.4"]]}
             :production {:resource-paths ["resources-prod"]
                          :jvm-opts ["-Xmx1g" "-server"]}}
  :main clinicico.worker.main)
