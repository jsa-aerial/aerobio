
{:name "strm-map"
 :path ""
 :func (fn[func val]
         (cond
           (pg/exit-map? val)
           (infof "End input stream with status: %s" (val ::status))

           (or (pg/eoi? val)
               (pg/done? val)) (pg/done)

           :else
           (func val)))

 :description "Streaming map. More accurately, this is an application of FUNC to a value VAL. The actual 'mapping' action arises from the data flow as defined by the job graph. So, the input should be a 'streaming' source node(s) and the output will be 'streamed' to the output nodes."
 }
