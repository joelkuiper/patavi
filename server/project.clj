(defproject patavi.server "0.2.4-SNAPSHOT"
  :description "Patavi is a distributed system for exposing R as WAMP"
  :license {:name "The MIT License"
            :url "http://opensource.org/licenses/MIT"
            :distribution :repo}
  :url "http://patavi.com"
  :repositories {"sonatype-nexus-snapshots" "https://oss.sonatype.org/content/repositories/snapshots"
                 "sonatype-oss-public" "https://oss.sonatype.org/content/groups/public/" }
  :plugins [[lein-environ "0.4.0"]]
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [compojure "1.1.5"]
                 [patavi.common "0.2.4-SNAPSHOT"]
                 [ring/ring-devel "1.2.0"]
                 [http-kit "2.1.13"]
                 [clj-wamp "1.0.0"]
                 [overtone/at-at "1.2.0"]
                 [org.zeromq/cljzmq "0.1.3" :exclusions [org.zeromq/jzmq]]
                 [liberator "0.9.0"]]
  :env {:broker-frontend-socket "ipc://frontend.ipc"
        :broker-updates-socket "ipc://updates.ipc"
        :broker-backend-socket "tcp://*:7740",
        :ws-origin-re "https?://.*"
        :ws-base-uri "http://api.patavi.com/"}
  :profiles {:uberjar {:aot :all}
             :dev {:dependencies [[criterium "0.4.2"]
                                  [org.clojure/tools.namespace "0.2.4"]
                                  [org.jeromq/jeromq "0.3.0-SNAPSHOT"]]}
             :production {:dependencies [[org.zeromq/jzmq "3.0.1"]]
                          :jvm-opts ["-server" "-Djava.library.path=/usr/lib:/usr/local/lib"]}}
  :main patavi.server.server)
