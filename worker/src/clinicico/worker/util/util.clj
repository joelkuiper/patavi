(ns clinicico.worker.util.util
  (:use   clojure.java.io
          clojure.walk
          [cheshire.generate :refer [add-encoder encode-str remove-encoder]])
  (:import (com.fasterxml.jackson.core JsonGenerator)
           (org.joda.time.format.ISODateTimeFormat))
  (:require [clojure.string :as s]))

(defn map-cols-to-rows
  "Transposes maps of the form

     {:a [1 2 3]
      :b [4 5 6]
      :c [7 8 9]
      :d nil}
   into a vector of

     [{:a 1 :b 4 :c 7}
      {:a 2 :b 5 :c 8}
      {:a 3 :b 6 :c 9}]]"
  [maps]
  (let [x (into {} (filter second maps))] ;; nil is falsey, so this removes nil values
    (map (fn [m] (zipmap (keys x) m)) (apply map vector (vals x)))))

(defn map-rows-to-cols
  "Reverses map-cols-to-rows. For example:

      [{:a 1 :b 4 :c 7}
       {:a 2 :b 5 :c 8}
       {:a 3 :b 6 :c 9}]]
   becomes

      {:a [1 2 3]
       :b [4 5 6]
       :c [7 8 9]}"
  [maps]
  (reduce (fn [m1 m2]
    (reduce (fn [m [k v]]
      (update-in m [k] (fnil conj []) v)) m1, m2)) {} maps))

(defn stringify-keys*
  "Recursively transforms all map keys to strings.
   Note that the clojure.walk/stringify-keys only stringifies keywords."
  [m]
  (let [f (fn [[k v]] (if (keyword? k) [(name k) v] [(str k) v]))]
    ;; only apply to maps
    (postwalk (fn [x] (if (map? x) (into {} (map f x)) x)) m)))

(defn map-difference
  "Calculates the difference between to maps, returns the k v of the difference.
   Stolen from [StackOverflow](http://stackoverflow.com/questions/3387155/difference-between-two-maps)"
  [m1 m2]
  (loop [m (transient {})
         ks (concat (keys m1) (keys m2))]
    (if-let [k (first ks)]
      (let [e1 (find m1 k)
            e2 (find m2 k)]
        (cond (and e1 e2 (not= (e1 1) (e2 1))) (recur (assoc! m k (e1 1)) (next ks))
              (not e1) (recur (assoc! m k (e2 1)) (next ks))
              (not e2) (recur (assoc! m k (e1 1)) (next ks))
              :else    (recur m (next ks))))
      (persistent! m))))

;; Make joda.time JSONable
(add-encoder org.joda.time.DateTime
  (fn [^org.joda.time.DateTime date ^JsonGenerator jg]
    (.writeString jg (.print (org.joda.time.format.ISODateTimeFormat/dateTime) date))))
