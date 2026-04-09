
{
 :name "align",
 :path "",
 :repeat true
 :generator true

 :func (let [proc (volatile! :notyetrun)
             outstream (volatile! nil)
             buf (byte-array (* 128 1040))]
         (fn [aligner & args]
           (println (format "AlignSRV %s %s" aligner args))
           (when (= @proc :notyetrun)
             (vswap! proc (fn[_] (pg/align aligner args)))
             (vswap! outstream (fn[_] (@proc :out))))
           (try
             (let [bytearray (pg/read-stream @outstream buf)]
               (cond
                 (nil? bytearray)
                 (let [ei (pg/exit-info @proc)]
                   (.close @outstream)
                   (infof "Aligner '%s' exit '%s'" aligner ei)
                   (pg/done))
                 
                 ;;(#{:bowtie :bowtie2} aligner)
                 ;;(apply str (mapv char bytearray))

                 :else
                 bytearray))
             (catch Exception e
               (infof "ALIGN '%s'" (or (.getMessage e) e))
               (pg/done))
             (finally
               (.close @outstream)))))
 
 ;; instructional data used in /help
 :description  "Function wrapping aligners for aligning reads to genomes, via dispatch on aligner (bowtie, bowtie2, STAR, etc)",
}
