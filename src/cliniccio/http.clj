(ns cliniccio.http
  (:use ring.util.response
        [clojure.string :only [upper-case join]]))

(defn url-from 
  [{scheme :scheme server-name :server-name server-port :server-port uri :uri}
   & path-elements]
  (str "http://" server-name ":" server-port  uri "/" (join "/" path-elements)))

(defn options
  ([] (options #{:options} nil))
  ([allowed] (options allowed nil))
  ([allowed body]
   (->
     (response body)
     (header "Allow" (join ", " (map (comp upper-case name) allowed))))))

(defn method-not-allowed
  [allowed]
  (->
    (options allowed)
    (status 405)))

(defn no-content? [body]
  (if (or (nil? body) (empty? body))
    (->
      (response nil)
      (status 204))
    (response body)))

(defn not-implemented []
  (->
    (response nil)
    (status 501)))

(defn created
  ([url]
   (created url nil))
  ([url body]
   (->
     (response body)
     (status 201)
     (header "Location" url))))
