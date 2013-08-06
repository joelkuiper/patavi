(defproject clinicico.server "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [compojure "1.1.5"]
                 [ring/ring-devel "1.2.0"]
                 [ring.middleware.jsonp "0.1.3"]
                 [org.clojure/tools.cli "0.2.2"]
                 [com.taoensso/nippy "2.1.0"]
                 [http-kit "2.1.8"]
                 [cheshire "5.2.0"]
                 [liberator "0.9.0"]
                 [log4j/log4j "1.2.17"]
                 [org.clojure/tools.nrepl "0.2.3"]
                 [inflections "0.8.1"]
                 [clj-time "0.5.1"]
                 [org.clojure/tools.logging "0.2.6"]
                 [org.clojure/data.xml "0.0.7"]
                 [com.novemberain/monger "1.6.0"]
                 [org.zeromq/cljzmq "0.1.1"]]
  :jvm-opts ["-Djava.library.path=/usr/lib:/usr/local/lib"]
  :main clinicico.server.server)
