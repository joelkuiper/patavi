(ns cliniccio.util
  (:use   clojure.java.io
          clojure.walk)
  (:require [clojure.string :as s]))

(def not-nil? (complement nil?))

(defn map-cols-to-rows 
  [maps]
  (let [x (into {} (filter second maps))] ;; nil is falsey, so this removes nil values
    (map (fn [m] (zipmap (keys x) m)) (apply map vector (vals x)))))

(defn map-rows-to-cols 
  [maps]
  (reduce (fn [m1 m2]
    (reduce (fn [m [k v]] 
      (update-in m [k] (fnil conj []) v)) m1, m2)) {} maps))

(defn uuid [] (str (java.util.UUID/randomUUID)))

(defn stringify-keys*
  "Recursively transforms all map keys from keywords to strings."
  [m]
  (let [f (fn [[k v]] (if (keyword? k) [(name k) v] [(str k) v]))]
    ;; only apply to maps
    (postwalk (fn [x] (if (map? x) (into {} (map f x)) x)) m)))
