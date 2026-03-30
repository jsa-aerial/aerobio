
{:name "aggregate"
 :path ""
 :func (let [result (volatile! [])
             already-sent? (volatile! false)]
         (fn[data]
           (if (and (pg/eoi? data) (pg/done? data))
             (do (infof "AGGREGATE node sent EOI/DONE: %s" data)
                 (if @already-sent?
                   (do (infof "AGGREGATE node already returned result")
                       (pg/done))
                   (do (vswap! already-sent? (fn[v] true))
                       @result)))
             (do
               (vswap! result (fn[resval] (conj resval data)))
               (pg/need)))))

 :description "Streaming aggregation. Takes a stream of data and constructs a vector containing all data. This is mostly for aggregating result maps of upstream nodes. Similar to eager seqs in that all data will be used at once. BE CAREFUL."
 }
