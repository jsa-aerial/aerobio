{
 :name "bowtie1-make-maps",
 :path "",
 :func (let [ot (volatile! nil)
             leftover (volatile! "")
             m {0 "+", 16 "-"}]
         (fn [map-file rec]
           (when (nil? (deref ot))
             (infof "bowtie1-make-maps: Opening %s" map-file)
             (vswap! ot (fn[_ f] (aio/open-file f :out)) map-file))
           (if (pg/eoi? rec)
             (if (pg/done? rec)
               (do
                 (infof "Closing stream %s" (deref ot))
                 (pg/close-stream (deref ot))
                 (infof "Finished writing %s" map-file))
               ;;Else
               (infof "End input stream: %s, REC: %s" (type rec) rec))
             (let [instg (str (deref leftover) (String. rec))
                   eolnl? (= \newline (austr/get instg (dec (count instg))))
                   lines (str/split instg #"\n")
                   [lines lo] (if (not eolnl?)
                                [(butlast lines) (last lines)]
                                [lines ""])
                   ot (deref ot)]
               (vswap! leftover (fn[_] lo))
               (doseq [line lines]
                 (let [rec (str/split line #"\t")
                       l (str/join
                          "\t"
                          [(-> (rec 0) (str/split #"-") second)
                           (rec 1)
                           (rec 2)
                           (rec 3)
                           (count (rec 4))])]
                   (.write ot l)
                   (.newLine ot)))))))

 :description "Take streaming obsolete map records (generated from bowtie1 from a _collapsed_ input file), pull out collapse count, strand info, position, and sq length, assemble with tabs and write resulting line to file map-file. This map-file will be used as input for calc_fitness operation."
}
