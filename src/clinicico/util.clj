(ns clinicico.util
  (:use   clojure.java.io
          clojure.walk)
  (:require [clojure.string :as s]))

(def not-nil? (complement nil?))
(defn uuid [] (str (java.util.UUID/randomUUID)))

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
