(defproject patavi.common "0.2.4-SNAPSHOT"
  :description "Library with common functions and dependencies for server and worker"
  :url "http://patavi.com"
  :license {:name "The MIT License"
            :url "http://opensource.org/licenses/MIT"
            :distribution :repo}
  :repositories {"sonatype-nexus-snapshots" "https://oss.sonatype.org/content/repositories/snapshots"
                 "sonatype-oss-public" "https://oss.sonatype.org/content/groups/public/" }
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/core.async "0.1.242.0-44b1e3-alpha"]
                 [org.clojure/tools.logging "0.2.6"]
                 [org.clojure/tools.cli "0.2.4"]
                 [com.taoensso/nippy "2.4.1"]
                 [environ "0.4.0"]
                 [cheshire "5.2.0"]
                 [log4j "1.2.17" :exclusions [javax.mail/mail
                                              javax.jms/jms
                                              com.sun.jdmk/jmxtools
                                              com.sun.jmx/jmxri]]
                 [org.zeromq/cljzmq "0.1.3" :exclusions [org.zeromq/jzmq]]
                 [com.google.guava/guava "15.0"]
                 [clj-time "0.6.0"]
                 [crypto-random "1.1.0"]])
