
{:name "get-comparison-files"
 :path ""
 :repeat true
 :generator true
 :func (let [cmpgrps (volatile! :nyi)]
         (fn[eid comp-filename opt?]
           (when (= @cmpgrps :nyi)
             (vswap! cmpgrps
                     (fn[_]
                       (cmn/get-comparison-files
                        (cmn/get-exp-info eid :exp)
                        eid comp-filename opt?))))
           (let [grp @cmpgrps]
             (if (seq grp)
               (let [p (first grp)]
                 (vswap! cmpgrps (fn[_] (rest grp)))
                 p)
               (pg/done)))))

 :description "Streaming comparison input data grp. For RNA-Seq, each element is a vector [bams csv] where bams are the input bams to count and csv is the output file for the count matrix. For Tn-Seq, each element is a quad [t1 t2 csv ef], where t1 and t2 are the condition map files, csv the out matrix file, and ef the expansion factor."
 }
