{:name "wait"
 :path ""
 :func (let [buf (byte-array (* 64 1040))
             ot (volatile! nil)]
         (fn [file x data]
           (when (nil? (deref ot))
             (vswap! ot (fn[_ f] (io/output-stream f)) file))
           (cond
             (= (type data) clojure.lang.PersistentArrayMap)
             (prn "Type data is  " (type data) :DATA data)

             (pg/byte-array? data)
             (pg/write-stream (deref ot) data)

             :else
             (do (prn "Closing stream " ot)
                 (pg/close-stream (deref ot))
                 (if (= data ::pg/done)
                   (do (prn "Returning " x)
                       x)
                   false)))))
 }
