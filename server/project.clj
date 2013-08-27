(defproject clinicico.server "0.1.0-SNAPSHOT"
  :description "Clinici.co is a distributed system for exposing R as RESTful web services"
  :license {:name "The MIT License"
            :url "http://opensource.org/licenses/MIT"
            :distribution :repo}
  :url "http://clinici.co"
  :repositories {"sonatype-nexus-snapshots" "https://oss.sonatype.org/content/repositories/snapshots"
                 "sonatype-oss-public" "https://oss.sonatype.org/content/groups/public/" }
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [compojure "1.1.5"]
                 [clinicico.common "0.1.0-SNAPSHOT"]
                 [ring/ring-devel "1.2.0"]
                 [http-kit "2.1.9"]
                 [clj-wamp "1.0.0"]
                 [overtone/at-at "1.2.0"]
                 [org.zeromq/cljzmq "0.1.1" :exclusions [org.zeromq/jzmq]]
                 [liberator "0.9.0"]]
  :profiles {:dev {:resource-paths ["resources-dev"]
                   :dependencies [[org.clojure/tools.namespace "0.2.4"]
                                  [org.jeromq/jeromq "0.3.0-SNAPSHOT"]]}
             :production {:dependencies [[org.zeromq/jzmq "2.2.2"]]
                          :resource-paths ["resources-prod"]
                          :jvm-opts ["-server" "-Djava.library.path=/usr/lib:/usr/local/lib"]}}
  :main clinicico.server.server)
