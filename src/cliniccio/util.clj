
(ns cliniccio.util) 

(defn transpose-map [maps]
  (let [x (into {} (filter second maps))] ;; Second is false when nil, so this removes nil values
    (map (fn [m] (zipmap (keys x) m)) (apply map vector (vals x)))))
