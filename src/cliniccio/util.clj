
(ns cliniccio.util) 

(defn map-cols-to-rows [maps]
  (let [x (into {} (filter second maps))] ;; nil is falsey, so this removes nil values
    (map (fn [m] (zipmap (keys x) m)) (apply map vector (vals x)))))

(defn map-rows-to-cols [maps]
  (reduce (fn [m1 m2]
    (reduce (fn [m [k v]] 
      (update-in m [k] (fnil conj []) v)) m1, m2)) {} maps))
