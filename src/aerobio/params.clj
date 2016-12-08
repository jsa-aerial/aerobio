(ns aerobio.params)

(def params (atom {}))

(defn param-ks []
  (keys @params))

(defn param-set [] @params)

(defn get-params [& ks]
  (if (= 1 (count ks))
    (@params (first ks))
    (mapv @params ks)))

