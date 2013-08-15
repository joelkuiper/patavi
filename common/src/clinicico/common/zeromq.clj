(ns clinicico.common.zeromq
  (:gen-class)
  (:require [zeromq.zmq :as zmq])
  (:import [org.zeromq ZMQ ZMQ$Socket ZMQ$PollItem ZLoop ZLoop$IZLoopHandler]))

(def STATUS-OK (byte-array (byte 1)))
(def STATUS-ERROR (byte-array (byte 2)))

; message types for router/dealer with ping/pong heart beats
(def ^:const MSG-READY (byte 1))
(def ^:const MSG-PING (byte 2))
(def ^:const MSG-PONG (byte 3))
(def ^:const MSG-REQ (byte 4))
(def ^:const MSG-REP (byte 5))

(defn status-ok? [status] (java.util.Arrays/equals status STATUS-OK))

(defmulti bytes-from (fn [arg] (class arg)))
(defmethod bytes-from String [arg] (.getBytes arg))
(defmethod bytes-from Byte [arg] (byte-array [arg]))
(defmethod bytes-from zmq/bytes-type [arg] arg)

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
(defmethod bytes-to Byte [barr _] (aget barr 0))

(defn- receive-empty [socket]
  (if (not (= 0 (alength (zmq/receive socket))))
    (throw (IllegalStateException. "Got non-empty message where empty message expected"))))

(defn receive
  ([^ZMQ$Socket socket ^Iterable types]
   (let [t (first types)
         msg (bytes-to (zmq/receive socket) t)]
     (if (empty? (rest types))
       [msg]
       (do
         (receive-empty socket)
         (cons msg (receive socket (rest types)))))))
   ([^ZMQ$Socket socket ^Number n ^Class t]
      (receive socket (repeat n t))))

(defn receive-more
  ([^ZMQ$Socket socket ^Iterable types]
   (receive-empty socket)
   (receive socket types))
  ([^ZMQ$Socket socket ^Number n ^Class t]
   (receive-empty socket)
   (receive socket (repeat n t))))

(defn zloop-handler
  [handle-fn]
  (proxy [ZLoop$IZLoopHandler] []
    (handle [^ZLoop _ ^ZMQ$PollItem _ ^Object _]
      (handle-fn))))
