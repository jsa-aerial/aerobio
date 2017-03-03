
{:name "aggregate"
 :path ""
 :func (let [result (volatile! [])]
         (fn[data]
           (if (and (pg/eoi? data) (pg/done? data))
             @result
             (do
               (vswap! result (fn[resval] (conj resval data)))
               (pg/need)))))

 :description "Streaming aggregation. Takes a stream of data and constructs a vector containing all data. This is mostly for aggregating result maps of upstream nodes. Similar to eager seqs in that all data will be used at once. BE CAREFUL."
 }
