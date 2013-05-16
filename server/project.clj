(defproject clinicico.server "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [compojure "1.1.5"]
                 [ring/ring-devel "1.1.8"]
                 [com.taoensso/nippy "1.2.0"]
                 [http-kit "2.1.2"]
                 [cheshire "5.1.1"]
                 [liberator "0.8.0"]
                 [org.clojure/tools.logging "0.2.4"]
                 [log4j/log4j "1.2.17"]
                 [inflections "0.8.1"]
                 [clj-time "0.5.0"]
                 [org.clojure/data.xml "0.0.7"]
                 [com.novemberain/monger "1.6.0-beta2"]
                 [com.novemberain/langohr "1.0.0-beta13"]]
  :main clinicico.server.handler)
