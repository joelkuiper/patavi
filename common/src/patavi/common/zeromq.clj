(ns patavi.common.zeromq
  (:require [zeromq.zmq :as zmq]
            [patavi.common.util :refer :all]
            [crypto.random :as crypto])
  (:import [org.zeromq ZMQ ZMQ$Socket
                       ZMQ$PollItem ZMsg ZFrame
                       ZLoop ZLoop$IZLoopHandler]))

(def STATUS-OK (byte-array (byte 0x1)))
(def STATUS-ERROR (byte-array (byte 0x2)))

; message types for router/dealer with ping/pong heart beats
(def ^:const MSG-READY (byte 0x1))
(def ^:const MSG-PING (byte 0x2))
(def ^:const MSG-PONG (byte 0x3))
(def ^:const MSG-REQ (byte 0x4))
(def ^:const MSG-REP (byte 0x5))
(def ^:const MSG-UPDATE (byte 0x6))

(defn status-ok? [status] (java.util.Arrays/equals status STATUS-OK))

(defn create-connected-socket
  "Creates a connected ZMQ socket, optionally
   identified with ident, set a random ident if none provided"
  ([context type address]
     (create-connected-socket context type address (crypto.random/hex 8)))
  ([context type address ident]
     (zmq/connect
      (zmq/set-identity
       (zmq/socket context type) (.getBytes ident)) address)))

(defmulti bytes-from (fn [arg] (class arg)))
(defmethod bytes-from String [arg] (.getBytes arg))
(defmethod bytes-from Byte [arg] (byte-array [arg]))
(defmethod bytes-from zmq/bytes-type [arg] arg)

(defmulti bytes-to (fn [_ arg] arg))
(defmethod bytes-to zmq/bytes-type [barr _] barr)
(defmethod bytes-to String [barr _] (String. barr))
(defmethod bytes-to Byte [barr _] (aget barr 0))

(def ^:private empty-frame (ZFrame. (byte-array 0)))

(defn send!
  "Sends a vector of parts over the ZMQ socket as a `ZMsg`.
   Optionally receives :prefix-empty which adds a zero byte as first part"
  [^ZMQ$Socket socket parts & flags]
  (let [frames (map #(ZFrame. (bytes-from %)) parts)
        content (interleave frames (repeat empty-frame))
        msg (ZMsg.)]
    (if (contains? (set flags) :prefix-empty)
      (.addAll msg (concat [empty-frame] content))
      (.addAll msg content))
    (.send msg socket)))

(defn retrieve-data
  "Retrieves the data from a ZMsg"
  [^ZMsg msg types & flags]
  (when (contains? (set flags) :drop-first)
    (.destroy (.pop msg)))
  (loop [acc [] msg msg types types]
    (if (and (seq types) (not (empty? msg)))
      (let [^ZFrame frame (.unwrap msg)
            content (bytes-to (.getData frame) (first types))]
        (.destroy frame)
        (recur (conj acc content) msg (rest types)))
      acc)))

(defn receive!
  ([^ZMQ$Socket socket]
     (ZMsg/recvMsg socket))
  ([^ZMQ$Socket socket ^Iterable types]
     (let [^ZMsg msg (ZMsg/recvMsg socket)]
       (retrieve-data msg types)))
  ([^ZMQ$Socket socket ^Number n ^Class t]
     (receive! socket (repeat n t))))

(defn zloop-handler
  [handle-fn]
  (proxy [ZLoop$IZLoopHandler] []
    (handle [^ZLoop _ ^ZMQ$PollItem _ ^Object _]
      (handle-fn))))
