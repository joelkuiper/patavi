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
                 [patavi.common "0.2.3"]
                 [org.zeromq/cljzmq "0.1.3" :exclusions [org.zeromq/jzmq]]
                 [org.rosuda/REngine "1.7.1-SNAPSHOT"]]
  :profiles {:uberjar {:aot :all}
             :dev {:resource-paths ["resources-dev" "resources"]
                   :dependencies [[org.clojure/tools.namespace "0.2.4"]
                                  [org.jeromq/jeromq "0.3.0-SNAPSHOT"]]}
             :production {:dependencies [[org.zeromq/jzmq "3.0.1"]]
                          :resource-paths ["resources-prod" "resources"]
                          :jvm-opts ["-server" "-Djava.library.path=/usr/lib:/usr/local/lib" ]}}
  :main patavi.worker.main)
