
{
 :name "make-map",
 :path "",
 :func (let [ot (volatile! nil)
             leftover (volatile! "")
             rv (volatile! {})
             mean aerial.utils.math.probs-stats/mean
             m {0 "+", 16 "-"}]
         (fn [map-file rec]
           (when (nil? (deref ot))
             (infof "make-maps: Opening %s" map-file)
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
               (doseq [l lines]
                 (let [r @rv
                       rec (str/split l #"\t")
                       [cnt std pos len]
                       [(-> (rec 0) (str/split #"-") second Integer/parseInt)
                        (-> (rec 1) Integer/parseInt m)
                        (-> (rec 3) Integer/parseInt dec)
                        (count (rec 9))]
                       rl (r :len)]
                   (cond
                     (empty? r)
                     (vswap!
                      rv (fn[r] (assoc r :cnt cnt :std std :pos pos :len len)))

                     (and (not= r {})(< (Math/abs (- pos (r :pos))) 3)
                          (= (r :std) std))
                     (vswap!
                      rv (fn[r]
                           (assoc r :pos (long (mean [pos (r :pos)]))
                                  :cnt (+ (r :cnt) cnt)
                                  :len (if (> len rl) len rl))))
                     :else
                     (let [l (str/join #"\t" [(r :cnt) (r :std)
                                              (r :pos) (r :len)])]
                       (vswap!
                        rv (fn[r] (assoc r :cnt cnt :std std :pos pos :len len)))
                       (.write ot l)
                       (.newLine ot)))))))))

 :description "Take streaming SAM records (generated from bowtie from a _collapsed_ input file), pull out collapse count, strand info, position, and sq length, assemble with tabs and write resulting line to file map-file. This map-file will be used as input for calc_fitness operation."
}
