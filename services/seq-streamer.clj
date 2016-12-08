
{:name "seq-streamer"
 :path ""
 :func (fn [in-resource & _]
         (if (pg/eoi? in-resource)
           (pg/done)
           (let [buf (byte-array (* 64 1040))
                 input (->> in-resource io/as-url input-stream)]
             
             (pg/close-stream input)
             )))

 :description ""
 }
