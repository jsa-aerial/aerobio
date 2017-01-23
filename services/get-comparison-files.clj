
{:name "get-comparison-files"
 :path ""
 :repeat true
 :generator true
 :func (let [bam-ot-pairs (volatile! :nyi)]
         (fn[eid comp-filename rep?]
           (when (= @bam-ot-pairs :nyi)
             (vswap! bam-ot-pairs
                     (fn[_] (htrs/get-comparison-files eid comp-filename rep?))))
           (let [pairs @bam-ot-pairs]
             (if (seq pairs)
               (let [p (first pairs)]
                 (vswap! bam-ot-pairs (fn[_] (rest pairs)))
                 p)
               (pg/done)))))

 :description "Streaming comparison input data pairs. Each element is a vector [bams csv] where bams are the input bams to count and csv is the output file for the count matrix."
 }
