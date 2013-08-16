(defproject clinicico.server "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [compojure "1.1.5"]
                 [clinicico.common "0.1.0-SNAPSHOT"]
                 [ring/ring-devel "1.2.0"]
                 [ring.middleware.jsonp "0.1.3"]
                 [org.clojure/tools.cli "0.2.2"]
                 [http-kit "2.1.8"]
                 [cheshire "5.2.0"]
                 [overtone/at-at "1.2.0"]
                 [liberator "0.9.0"]
                 [org.clojure/tools.nrepl "0.2.3"]
                 [inflections "0.8.1"]
                 [org.clojure/data.xml "0.0.7"]
                 [com.novemberain/monger "1.6.0"]]
  :main clinicico.server.server)
