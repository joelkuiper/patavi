(ns clinicico.common.zeromq
  (:gen-class)
  (:require [zeromq.zmq :as zmq])
  (:import [org.zeromq ZMQ ZMQ$Socket]))

(def STATUS-OK (byte-array (byte 1)))
(def STATUS-ERROR (byte-array (byte 2)))

(defn status-ok? [status] (java.util.Arrays/equals status STATUS-OK))

(defn send-frame
  [socket & args]
  (let [msg (first args)
        msg-bytes (if (instance? String msg) (.getBytes msg) msg)]
    (if (empty? (rest args))
      (zmq/send socket msg-bytes)
      (do
        (zmq/send socket msg-bytes ZMQ/SNDMORE)
        (zmq/send socket (byte-array 0) ZMQ/SNDMORE)
        (recur socket (rest args))))))

(defmulti bytes-to (fn [_ arg] arg))
(defmethod bytes-to zmq/bytes-type [barr _] barr)
(defmethod bytes-to String [barr _] (String. barr))

(defn- take-empty [socket]
  (if (not (= 0 (alength (zmq/receive socket))))
    (throw (IllegalStateException. "Got non-empty message where empty message expected"))))

(defn take
  ([^ZMQ$Socket socket ^Iterable types]
   (let [t (first types)
         msg (bytes-to (zmq/receive socket) t)]
     (if (empty? (rest types))
       [msg]
       (do
         (take-empty socket)
         (cons msg (take socket (rest types)))))))
  ([^ZMQ$Socket socket ^Number n ^Class t]
   (take socket (repeat n t))))

(defn take-more
  ([^ZMQ$Socket socket ^Iterable types]
   (take-empty socket)
   (take socket types))
  ([^ZMQ$Socket socket ^Number n ^Class t]
   (take-empty socket)
   (take socket (repeat n t))))
