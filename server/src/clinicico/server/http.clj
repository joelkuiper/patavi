;; ## HTTP
;; These functions provide helpers for creating Ring responses.
;; They should only be used when appropriate, see associated HTTP Spec. for
;; detaills. Note that each of the Middleware handlers can abort the request
;; handling and can sent its own response body.

(ns clinicico.server.http
  (:require [ring.util.response :refer :all]
            [clojure.string :refer [upper-case blank? join]]))

(defn options
  "The OPTIONS method represents a request for information about the communication
   options available on the request/response chain identified by the Request-URI.
   This method allows the client to determine the options and/or requirements
   associated with a resource, or the capabilities of a server,
   without implying a resource action or initiating a resource retrieval.
   Source: [HTTP Spec.](http://www.w3.org/Protocols/rfc2616/rfc2616-sec9.html#sec.9.2)"
  ([] (options #{:options} nil))
  ([allowed] (options allowed nil))
  ([allowed body]
   (->
     (response body)
     (header "Allow" (join ", " (map (comp upper-case name) allowed))))))

(defn method-not-allowed
  "The method specified in the Request-Line is not allowed for the resource
   identified by the Request-URI. The response MUST include an Allow header
   containing a list of valid methods for the requested resource.
   Source: [HTTP Spec.](http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html#sec10.4.6)"
  [allowed]
  (->
    (options allowed)
    (status 405)))

(defn no-content?
  "The server has fulfilled the request but does not need to return an entity-body,
   and might want to return updated metainformation. The response MAY include new or
   updated metainformation in the form of entity-headers,
   which if present SHOULD be associated with the requested variant.
   Source: [HTTP Spec.](http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html#sec10.2.5)"
  [body]
  (if (or (nil? body) (empty? body))
    (->
      (response nil)
      (status 204))
    (response body)))

(defn not-implemented
  "the server does not support the functionality required to fulfill the request.
   this is the appropriate response when the server does not recognize the
   request method and is not capable of supporting it for any resource.
   source: [http spec.](http://www.w3.org/protocols/rfc2616/rfc2616-sec10.html#sec10.5.2)"
  []
  (->
    (response nil)
    (status 501)))

(defn created
  "The request has been fulfilled and resulted in a new resource being created.
   The newly created resource can be referenced by the URI(s) returned in the
   entity of the response, with the most specific URI for the resource given by a Location header field.
   The response SHOULD include an entity containing a list of resource characteristics and
   location(s) from which the user or user agent can choose the one most appropriate.
   Source: [HTTP Spec.](http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html#sec10.2.2) "
  ([url]
   (created url nil))
  ([url body]
   (->
     (response body)
     (status 201)
     (header "Location" url))))
