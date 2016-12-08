{:name "write-bam-then-cufflinks"
 :path ""
 :func (let [buf (byte-array (* 64 1040))
             ot (volatile! nil)]
         (fn [bam-file ref-gtf cuff-ot-dir data]
           (when (nil? (deref ot))
             (infof "write-bam-then-cufflinks: Opening %s" bam-file)
             (vswap! ot (fn[_ f] (io/output-stream f)) bam-file))
           (cond
             (pg/byte-array? data)
             (pg/write-stream (deref ot) data)

             (pg/eoi? data)
             (if (pg/done? data)
               (do
                 (infof "Closing stream %s" ot)
                 (pg/close-stream (deref ot))
                 (infof "Running cufflinks on %s" bam-file)
                 (pg/cufflinks "-p" "16" "--overlap-radius" "1"
                               "-g" ref-gtf
                               "-o" cuff-ot-dir
                               bam-file)
                 (infof "cufflinks finished: %s" cuff-ot-dir)
                 (str bam-file " -> cufflinks -> " cuff-ot-dir))
               ;; Else
               (infof "End input stream: %s, DATA: %s" (type data) data))

             :else
             (warnf "WRITE-BAM-THEN-CUFFLINKS, bad input %s" data))))

 :description "Streaming to cufflinks stdin seems to cause cufflinks to not include all assembly data(?!?), so in order to achieve some streaming benefits, this function will write the bam (typically the output of a samtools sort run) to bam-file, and upon eos for that, close the file, then call cufflinks on it with output to cuff-ot-dir and using the reference gtf annotation file ref-gtf"
 }
