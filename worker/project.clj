(defproject patavi.worker "0.2.4-SNAPSHOT"
  :url "http://patavi.com"
  :license {:name "The MIT License"
            :url "http://opensource.org/licenses/MIT"
            :distribution :repo}
  :description "Workers listen for tasks and dispatch them to RServe"
  :repositories {"sonatype-nexus-snapshots" "https://oss.sonatype.org/content/repositories/snapshots"
                 "sonatype-oss-public" "https://oss.sonatype.org/content/groups/public/"
                 "drugis" "http://drugis.org/mvn"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [patavi.common "0.2.4-SNAPSHOT"]
                 [org.rosuda/REngine "1.7.1-SNAPSHOT"]]
  :profiles {:uberjar {:aot :all}
             :dev {:resource-paths ["resources-dev" "resources"]
                   :dependencies [[org.clojure/tools.namespace "0.2.4"]]}
             :production {:resource-paths ["resources-prod" "resources"]
                          :jvm-opts ["-server" ]}}
  :main patavi.worker.main)
