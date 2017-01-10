
{:name "strm-take"
 :path ""
 :repeat true
 :generator true
 :func (let [cnt (volatile! 0)
             chunk (volatile! [])
             reset-cnt (constantly 0)
             reset-chunk (constantly [])
             reset-send (fn[]
                          (let [ret @chunk]
                            (vswap! cnt reset-cnt)
                            (vswap! chunk reset-chunk)
                            ret))
             inc-add    (fn[v]
                          (vswap! cnt #(inc %))
                          (vswap! chunk #(conj % v))
                          (pg/need))]
         (fn[n v]
           (cond
             (pg/eoi? v) (if (> @cnt 0) (reset-send) (pg/done))
             (= @cnt n) (let [ret (reset-send)] (inc-add v) ret)
             :else
             (inc-add v))))

 :description "Streaming take"
 }
