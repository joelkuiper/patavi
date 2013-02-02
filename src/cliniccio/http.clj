(ns cliniccio.http
  (:use ring.util.response
        [clojure.string :only [upper-case join]]))

(defn url-from 
  ([{scheme :scheme server-name :server-name server-port :server-port uri :uri} & path-elements]
  (str "http://" server-name ":" server-port  uri "/" (join "/" path-elements)))

  ([{scheme :scheme server-name :server-name server-port :server-port}]
  (str "http://" server-name ":" server-port )))

