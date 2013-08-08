(ns clinicico.common.zeromq
  (:gen-class)
  (:require [zeromq.zmq :as zmq])
  (:import [org.zeromq ZMQ ZMQ$Socket]))

(def STATUS-OK (byte-array (byte 1)))
(def STATUS-ERROR (byte-array (byte 2)))

; message types for router/dealer with ping/pong heart beats
(defrecord MsgType [id])
(def MSG-READY (MsgType. (byte 1)))
(def MSG-PING (MsgType. (byte 2)))
(def MSG-PONG (MsgType. (byte 3)))
(def MSG-REQ (MsgType. (byte 4)))
(def MSG-REP (MsgType. (byte 5)))

(defn status-ok? [status] (java.util.Arrays/equals status STATUS-OK))

(defmulti bytes-from (fn [arg] (class arg)))
(defmethod bytes-from String [arg] (.getBytes arg))
(defmethod bytes-from zmq/bytes-type [arg] arg)
(defmethod bytes-from MsgType [arg] (byte-array [(:id arg)]))

(defn send-frame
  [socket & args]
  (let [msg (first args)]
    (if (empty? (rest args))
      (zmq/send socket (bytes-from msg))
      (do
        (zmq/send socket (bytes-from msg) ZMQ/SNDMORE)
        (zmq/send socket (byte-array 0) ZMQ/SNDMORE)
        (recur socket (rest args))))))

(defn send-frame-delim
  [socket & args]
  (do (zmq/send socket (byte-array 0) ZMQ/SNDMORE) (apply (partial send-frame socket) args)))

(defmulti bytes-to (fn [_ arg] arg))
(defmethod bytes-to zmq/bytes-type [barr _] barr)
(defmethod bytes-to String [barr _] (String. barr))
(defmethod bytes-to MsgType [barr _] (MsgType. (aget barr 0)))

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
