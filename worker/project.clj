(defproject clinicico.worker "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :repositories {"local" ~(str (.toURI (java.io.File. "third-party/repo")))}
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/tools.cli "0.2.2"]
                 [org.clojure/tools.logging "0.2.6"]
                 [crypto-random "1.1.0"]
                 [com.taoensso/nippy "2.1.0"]
                 [log4j/log4j "1.2.17"]
                 [cheshire "5.2.0"]
                 [com.novemberain/monger "1.6.0"]
                 [clj-time "0.5.1"]
                 [org.zeromq/cljzmq "0.1.1"]
                 [local/RserveEngine "1.7.0"]
                 [local/REngine "1.7.0"]]
  :jvm-opts ["-Djava.library.path=/usr/lib:/usr/local/lib"]
  :main clinicico.worker.main)
