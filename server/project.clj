(defproject patavi.server "0.2.4-SNAPSHOT"
  :description "Patavi is a distributed system for exposing R as WAMP"
  :license {:name "The MIT License"
            :url "http://opensource.org/licenses/MIT"
            :distribution :repo}
  :url "http://patavi.com"
  :repositories {"sonatype-nexus-snapshots" "https://oss.sonatype.org/content/repositories/snapshots"
                 "sonatype-oss-public" "https://oss.sonatype.org/content/groups/public/" }
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [compojure "1.1.5"]
                 [patavi.common "0.2.4-SNAPSHOT"]
                 [ring/ring-devel "1.2.0"]
                 [http-kit "2.1.13"]
                 [clj-wamp "1.0.0"]
                 [overtone/at-at "1.2.0"]
                 [liberator "0.9.0"]]
  :profiles {:uberjar {:aot :all}
             :dev {:resource-paths ["resources-dev"]
                   :dependencies [[criterium "0.4.2"]
                                  [org.clojure/tools.namespace "0.2.4"]]}
             :production {:resource-paths ["resources-prod"]
                          :jvm-opts ["-server"]}}
  :main patavi.server.server)
