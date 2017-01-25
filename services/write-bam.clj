{:name "write-bam"
 :path ""
 :func (let [buf (byte-array (* 64 1040))
             ot (volatile! nil)]
         (fn [bam-file data]
           (when (nil? (deref ot))
             (infof "write-bam: Opening %s" bam-file)
             (vswap! ot (fn[_ f] (io/output-stream f)) bam-file))
           (cond
             (pg/byte-array? data)
             (pg/write-stream (deref ot) data)

             (pg/eoi? data)
             (if (pg/done? data)
               (do
                 (infof "Closing stream %s" ot)
                 (pg/close-stream (deref ot))
                 (str "Finished writing " bam-file))
               ;; Else
               (infof "End input stream: %s, DATA: %s" (type data) data))

             :else
             (warnf "WRITE-BAM, bad input %s" data))))

 :description "Write a streaming bam to file bam-file; data is the byte stream."
 }
