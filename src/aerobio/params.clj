(ns aerobio.params)

(def params (atom {}))

(defn param-ks []
  (keys @params))

(defn param-set [] @params)

(defn get-params [& ks]
  (cond
    (and (= 1 (count ks)) (vector? (first ks))) (get-in @params (first ks))
    (= 1 (count ks)) (@params (first ks))
    :else (mapv @params ks)))

