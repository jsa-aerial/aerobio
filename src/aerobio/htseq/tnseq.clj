;;--------------------------------------------------------------------------;;
;;                                                                          ;;
;;                A E R O B I O . H T S E Q . T N S E Q                     ;;
;;                                                                          ;;
;; Permission is hereby granted, free of charge, to any person obtaining    ;;
;; a copy of this software and associated documentation files (the          ;;
;; "Software"), to deal in the Software without restriction, including      ;;
;; without limitation the rights to use, copy, modify, merge, publish,      ;;
;; distribute, sublicense, and/or sell copies of the Software, and to       ;;
;; permit persons to whom the Software is furnished to do so, subject to    ;;
;; the following conditions:                                                ;;
;;                                                                          ;;
;; The above copyright notice and this permission notice shall be           ;;
;; included in all copies or substantial portions of the Software.          ;;
;;                                                                          ;;
;; THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,          ;;
;; EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF       ;;
;; MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND                    ;;
;; NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE   ;;
;; LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION   ;;
;; OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION    ;;
;; WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.          ;;
;;                                                                          ;;
;; Author: Jon Anthony                                                      ;;
;;                                                                          ;;
;;--------------------------------------------------------------------------;;
;;

(ns aerobio.htseq.tnseq
  [:require
   [clojure.string :as cljstr]
   [clojure.data.csv :as csv]
   [clojure.set :as set]

   [aerial.fs :as fs]
   [aerial.utils.string :as str]
   [aerial.utils.coll :as coll]
   [aerial.utils.io :refer [letio] :as io]
   [aerial.utils.math :as m]
   [aerial.utils.math.infoth :as it]
   [aerial.bio.utils.files :as bufiles]

   [aerobio.params :as pams]
   [aerobio.htseq.common :as cmn]
   [aerobio.pgmgraph :as pg]])


(defn trim-initsq-and-adapter
  "Trim off the 5' initiation sequence (in traditional Tn-Seq this is
  the 'HiSeq initiation' sequence) and the trailing 3' adapter
  sequence. Removing makes alignment more robust and simplifies
  transposon and barcode analysis. S is the starting base to
  keep (0-based) and E denotes the final base to keep, where E is
  typically given as a negative integer indicating how much to remove
  from the 3' end. In traditional Tn-Seq S is 9 while E is -17."
  [sq s e]
  (str/substring sq s e))

(defn position-transpose-prefix
  "Finds the 'best' match of tpn to the end of sq. Does not use
  optimal alignment scoring (edit distance), but a simplified Hamming
  distance with 'shrinking' window. Returns the index into sq of the
  best start location."
  [sq tpn]
  (let [mxtpn (count tpn)
        minsq (- (count sq) mxtpn)
        idxs0 (take (- mxtpn 2)
                    (iterate (fn[[x y]] [(inc x) (dec y)])
                             [minsq mxtpn]))
        maxsqi (->> idxs0 last first)]
    (loop [idxs idxs0]
      (let [[i j] (first idxs)
            m (if (< i maxsqi) 2 1)
            x (str/substring sq i (+ i j))
            y (str/substring tpn 0 j)]
        (cond (< (it/hamming x y) m) i
              (= i maxsqi) -1
              :else (recur (rest idxs)))))))

(defn seq-less-transposon
  [sq tpn]
  (let [i (position-transpose-prefix sq tpn)
        l (if (pos? i) (+ 2 i) i)]
    (when (pos? l)
      [(str/substring sq 0 l) l])))

(defn check-barcode
  [sq bc]
  (let [bcsz (count bc)]
    (->>  sq (str/sliding-take bcsz)
          (take 3)
          (map #(it/hamming bc %))
          (apply min) (>= 1))))

(defn trim-barcode
  [sq bcsz]
  (when sq
    (str/substring sq bcsz)))


(defn get-collapse-groups
  [eid]
  (let [base (cmn/get-exp-info eid :base)
        exp-illumina-xref (cmn/get-exp-info eid :exp-illumina-xref)
        illumina-sample-xref (cmn/get-exp-info eid :illumina-sample-xref)
        ifqs (cmn/get-bc-file-specs base exp-illumina-xref
                                    illumina-sample-xref)
        collapse-specs (cmn/get-bc-file-specs base exp-illumina-xref
                                              illumina-sample-xref
                                              :ftype ".collapse.fna")
        groups (map (fn[ibc]
                      [ibc (reduce (fn[V [ebc fq]]
                                     (conj V [fq (get-in collapse-specs
                                                         [ibc ebc])]))
                                   [] (ifqs ibc))])
                    (keys ifqs))]
    (mapv (fn [[ibc pairs]] pairs) groups)))

(defn write-farec
  [^java.io.BufferedWriter ot rec]
  (let [[id sq] rec]
    (.write ot (str id "\n"))
    (.write ot (str sq "\n"))))

(defn collapse-one [[fastq fasta]]
  (letio [inf (io/open-file fastq :in)
          otf (io/open-file fasta :out)
          cnt (loop [fqrec (bufiles/read-fqrec inf)
                     M {}]
                (if (nil? (fqrec 0))
                  (reduce (fn[C [sq cnt]]
                            (let [id (str ">" C "-" cnt)]
                              (write-farec otf [id sq])
                              (inc C)))
                          1 M)
                  (let [sq (fqrec 1)]
                    (recur (bufiles/read-fqrec inf)
                           (assoc M sq (inc (get M sq 0)))))))]
      {:name "collapser"
       :value [fasta cnt]
       :exit :success
       :err ""}))

(defn run-collapse-group
  [pairs]
  (let [futs (mapv (fn[pair] (future (collapse-one pair))) pairs)]
    (mapv (fn[fut] (deref fut)) futs)))


(defn pre-pass [fqrec]
  (let [[id sq aux qc] fqrec
        sq (trim-initsq-and-adapter sq 9 -17)
        qc (trim-initsq-and-adapter qc 9 -17)
        i  (position-transpose-prefix sq "TAACAG")
        i  (if (pos? i) (+ 2 i) i)
        [sq qc] (when (pos? i)[(str/substring sq 0 i) (str/substring qc 0 i)])]
    (when (pos? i)
      [id sq aux qc])))

(defn split-filter-fastqs
  [eid]
  (cmn/split-filter-fastqs eid pre-pass :baseqc% 0.90))



;;; Get primary phase 1 arguments. These are the bowtie index, the
;;; fastq set, the collapsed fastas, output map file, output bam and
;;; bai file names
(defmethod cmn/get-phase-1-args :tnseq
  [_ eid repname & {:keys [repk bowtie] :or {bowtie :bt1}}]
  (let [fqs (cljstr/join "," (cmn/get-replicate-fqzs eid repname repk))
        fnas (cljstr/join
              "," (mapv (fn[fq]
                          (->> fq (str/split #"\.")
                               (coll/takev-until #(= % "fastq"))
                               (cljstr/join ".")
                               (#(str % ".collapse.fna"))))
                        (str/split #"," fqs))) ;;_ (prn :FQS fqs :FNAS fnas)
        refnm (cmn/replicate-name->strain-name eid repname)
        bt1index (fs/join (cmn/get-exp-info eid :bt1index) refnm)
        bt2index (fs/join (cmn/get-exp-info eid :index) refnm)
        otbam (fs/join (cmn/get-exp-info eid repk :bams) (str repname ".bam"))
        otmap (fs/join (cmn/get-exp-info eid repk :maps) (str repname ".map"))
        otbai (str otbam ".bai")
        refgtf (fs/join (cmn/get-exp-info eid :refs)
                        (str refnm ".gtf"))]
    (apply cmn/ensure-dirs (map fs/dirname [otbam otbai otmap]))
    [bt2index fqs
     (if (= bowtie :bt1) bt1index bt2index) fnas
     otmap otbam otbai]))


(def offsets
  (let [llets "abcdefghijklmnopqrstuvwxyz"
        offset (-> "a" str/codepoints first dec)
        chints (mapv #(- % offset) (str/codepoints llets))
        offmap (into {} (map
                         (fn[[k v]] [(str k) v])
                         (partition 2 (interleave (seq llets) chints))))
        offmap (into offmap (map #(vector (str %) %) (range 1 27)))]
    offmap))

(defn get-comparison-files-
  "Compute the set of comparison pairs for tnseq fitness
  calculation. These pairs end up being pairs of corresponding map
  files of strain-condition-rep derived from the comparison pairs in
  the compfile. In the case of replicates, each pair must have the
  same rep-id. EID is the experiment id, and comp-filename is the name
  of the csv comparison file holding comparison records (pairs),
  default is ComparisonSheet.csv"
  ([eid aggr?]
   (get-comparison-files- eid "ComparisonSheet.csv" aggr?))
  ([eid comp-filename aggr?]
   (let [maps (cmn/get-exp-info eid :rep :maps)
         fit  (cmn/get-exp-info eid :rep :fit)
         aggr (cmn/get-exp-info eid :rep :aggrs)
         compvec (->> comp-filename
                      (fs/join (pams/get-params :nextseq-base) eid)
                      slurp csv/read-csv rest)
         mapsvec (mapv (fn[v]
                         (mapcat #(-> (fs/join maps (str % "-*.map"))
                                      fs/glob sort)
                                 (take 2 v))) ; strain-c1, strain-c2
                       compvec)
         mapsvec (mapv (fn[coll]
                         (->>
                          (group-by #(->> (fs/replace-type % "")
                                          fs/basename (str/split #"-")
                                          last)
                                    coll)
                          (filter (fn[[k v]] (= (count v) 2)))
                          (into {})))
                       mapsvec)
         cmpgrps (mapv #(let [crec %2
                              expfacts (coll/dropv 2 crec)]
                          (mapv (fn[[k v]]
                                  (let [crec (conj (coll/takev 2 crec) k)
                                        csv (str (cljstr/join "-" crec) ".csv")
                                        ef (expfacts (dec (offsets k)))]
                                    (conj v csv ef (offsets k))))
                                %1))
                       mapsvec
                       compvec)]
     (if aggr?
       (mapv (fn[grp]
               (when (seq grp)
                 (let [grp (sort-by last grp)
                       l (->> grp last first fs/basename (str/split #"-")
                              last (str/split #"\.") first)
                       g (mapv (fn[[_ _ csv _]]
                                 [(fs/join aggr csv) (fs/join fit csv)])
                               grp)
                       allcsv (->> g first first
                                   (#(fs/replace-type % (str "-" l ".csv"))))]
                   (conj g allcsv))))
             cmpgrps)
       ;; else fitness groups
       (mapcat (fn[grp]
                 (mapv (fn[[t1 t2 csv ef]] [t1 t2 (fs/join fit csv) ef]) grp))
               cmpgrps)
       ))))

(defmethod cmn/get-comparison-files :tnseq
  [_ & args]
  (apply get-comparison-files- args))


(defn get-phase-2-dirs [eid]
  (let [fit (cmn/get-exp-info eid :rep :fit)
        aggr (cmn/get-exp-info eid :rep :aggrs)]
    (cmn/ensure-dirs fit aggr)))

(defn run-fitness-aggregate
  [eid recipient comparison-file get-toolinfo template]
  (let [_ (get-phase-2-dirs eid)
        cfg    (assoc-in template
                         [:nodes :ph2 :args]
                         [eid comparison-file recipient])]
    (future (cmn/flow-program cfg get-toolinfo :run true))))

(defmethod cmn/run-comparison :tnseq
  [_ eid recipient compfile get-toolinfo template]
  (run-fitness-aggregate eid recipient compfile get-toolinfo template))

(defmethod cmn/run-phase-2 :tnseq
  [_ eid recipient get-toolinfo template]
  (run-fitness-aggregate
   eid recipient "ComparisonSheet.csv" get-toolinfo template))


(defn get-aggregate-files
  ([eid]
   (get-aggregate-files eid "AggregateSheet.csv"))
  ([eid aggr-filename]
   (let [aggr-file (fs/join (pams/get-params :nextseq-base)
                            eid aggr-filename)
         fit (cmn/get-exp-info eid :rep :fit)
         aggr (cmn/get-exp-info eid :rep :aggrs)
         recs (->> aggr-file slurp csv/read-csv rest
                   (mapv (fn[x] (filter #(not= "" %) x))))
         botnums (mapv last recs)
         filegrps (->> recs
                       (mapv (fn[v] (butlast v)))
                       (mapv (fn[v] (mapv #(fs/join fit (str % ".csv")) v))))
         namebits (mapv (fn[x]
                          (->> x (mapv fs/basename)
                               (map #(->> % (str/split #"-") butlast))))
                        filegrps)
         prenames (mapv #(->> % first (coll/takev 2) (cljstr/join "-"))
                        namebits)
         sufnames (->> namebits
                       (mapv #(->> % last last (str/butlast 1))))
         outnames (->> (interleave prenames
                                   sufnames
                                   (mapv #(format "BN-%s" %1) botnums))
                       (coll/partitionv-all 3)
                       (mapv #(fs/join aggr (cljstr/join "-" %)))
                       (mapv #(str % ".csv")))]
     (mapv vector filegrps outnames botnums))))

#_(->>  (get-aggregate-files eid "BottleNeck.csv") clojure.pprint/pprint)

(defn run-aggregation
  [eid recipient compfile get-toolinfo template]
  #_(prn eid compfile)
  (let [cfg (assoc-in template
                      [:nodes :ph2 :args]
                      [eid compfile recipient])]
    #_(prn cfg)
    (future (cmn/flow-program cfg get-toolinfo :run true))))




;;; ---- rewrite of calc_fitness ---- ;;;

(defn feature-map [gtf & {:keys [ftype] :or {ftype :any}}]
  (letio [lines (io/read-lines gtf)]
    ;; chm src ftype start end _ strand _ attrs(; separated, first gene_Id)
    (reduce (fn[M l]
              (let [lrec (->> l (str/split #"\t") vec)
                    rec [(-> 3 lrec Integer.) (-> 4 lrec Integer.) (lrec 6)
                         (->> 8 lrec (str/split #";") first
                              (str/split #"\s") second
                              (#(str/substring % 1 -1)))]]
                (if (or (= ftype :any) (= ftype (lrec 2)))
                  (assoc M (rec 0) rec)
                  M)))
            (sorted-map) lines)))

(defn read-mapfile
  [mapfile & {:keys [usestrand downstream] :or {usestrand :both}}]
  (letio [lines (io/read-lines mapfile)]
    (mapv persistent!
     (reduce (fn[[+cnts -cnts :as M] l]
               (let [[cnt strand start len] (str/split #"\t" l)
                     [cnt start len] (mapv #(Integer/parseInt %)[cnt start len])
                     cnt (long cnt), start (long start), len (long len)
                     pos (if (or (= strand "-") (not downstream))
                           start
                           ;; else + strand downstream
                           (+ start (- len 2)))]
                 (cond
                   (and (not= usestrand :both) (not= strand usestrand)) M
                   (= strand "+")
                   [(assoc! +cnts pos (+ cnt (long (+cnts pos 0)))) -cnts]
                   :else
                   [+cnts (assoc! -cnts pos (+ cnt (long (-cnts pos 0))))])))
             [(transient {}) (transient {})] lines))))

(def HDR ["position", "strand", "count_1", "count_2", "ratio",
          "mt_freq_t1", "mt_freq_t2", "pop_freq_t1", "pop_freq_t2",
          "gene", "D", "W", "nW"])

(defn fitness-recs
  [gtf map1 map2 args]
  (let [fmap (feature-map gtf)
        [+cnts1 -cnts1] (read-mapfile map1)
        [+cnts2 -cnts2] (read-mapfile map2)
        tot1 (long (m/sum (mapv #(m/sum (vals %)) [+cnts1 -cnts1])))
        tot2 (long (m/sum (mapv #(m/sum (vals %)) [+cnts2 -cnts2])))
        avgtot (-> tot1 (+ tot2) (/ 2.0))
        cfactor1 (/ (double tot1) avgtot)
        cfactor2 (/ (double tot2) avgtot)
        st1fn #(cond (and (+cnts1 %) (-cnts1 %)) "b"
                     (+cnts1 %) "+"  (-cnts1 %) "-" :else "")
        st2fn #(cond (and (+cnts2 %) (-cnts2 %)) "b"
                     (+cnts2 %) "+"  (-cnts2 %) "-" :else "")
        subdelta 7000
        expfact (double (args :expansion))
        cutoff (double (args :cutoff))]

    (loop [positions (sort (set/union (set (keys +cnts1)) (set (keys -cnts1))))
           recs [HDR]]
      (if (empty? positions)
        recs
        (let [i (long (first positions))
              strand (str (st1fn i) "/" (st2fn i))
              c1 (/ (+ (long (+cnts1 i 0)) (long (-cnts1 i 0))) cfactor1)
              c2 (/ (+ (long (+cnts2 i 0)) (long (-cnts2 i 0))) cfactor2)
              ratio (/ c2 c1)] ; may be 0 if c2 0, c1 can't be 0 by def.
          (if (< (/ (+ c1 c2) 2.0) cutoff)
            (recur (rest positions) recs)
            (let [mutfreq-t1 (/ c1 avgtot)
                  mutfreq-t2 (/ c2 avgtot)
                  popfreq-t1 (- 1.0 mutfreq-t1)
                  popfreq-t2 (- 1.0 mutfreq-t2)
                  fit (if (= mutfreq-t2 0.0)
                        0.0
                        (/ (m/ln (* mutfreq-t2 (/ expfact mutfreq-t1)))
                           (m/ln (* popfreq-t2 (/ expfact popfreq-t1)))))
                  gene (reduce
                        (fn[g [s [_ e _ tag]]] (if (<= s i e) (reduced tag) g))
                        "" (subseq fmap >= (- i subdelta) <= (+ i subdelta)))
                  rec [i strand c1 c2 ratio
                       mutfreq-t1 mutfreq-t2 popfreq-t1 popfreq-t2
                       gene expfact fit fit]]
              (recur (rest positions)
                     (conj recs rec)))))))))

(defn normalize-recs
  [recs args]
  (let [cutoff2 (args :cutoff2)
        max-weight (args :max-weight)
        x (reduce)]))

(defn tnseq-fitness
  [])





(comment

(let [cmpgrps (get-comparison-files eid true)
      args {:expansion 300, :cutoff 0, :cutoff2 10,
            :max-weight 75, :usestrand :both}
      x (time (mapv ;coll/vfold
               (fn[[t1 t2 csv expfact]]
                 (fitness-recs
                  "/Refs/NC_012469.gtf"
                  t1 t2 (assoc args :expansion (Double. expfact))))
               cmpgrps))]
  (mapv count x))


(let [eid "170206_NS500751_0027_AHFCWCBGX2"
      base (cmn/get-exp-info eid :out)
      x (time (fitness-recs
               "/Refs/NC_012469.gtf"
               (fs/join base "Rep/Maps/19F-d1504T1-1.map")
               (fs/join base "Rep/Maps/19F-d1504NoAbT2-1.map")
               {:expansion 300, :cutoff 0, :cutoff2 10,
                :max-weight 75, :usestrand :both}))]
  [(count x) (coll/takev 2 (rest x))])


(let [eid "170206_NS500751_0027_AHFCWCBGX2"
      base (cmn/get-exp-info eid :out)
      map1 (fs/join base "Rep/Maps/19F-d1504T1-1.map")
      map2 (fs/join base "Rep/Maps/19F-d1504NoAbT2-1.map")
      gtf "/Refs/NC_012469.gtf"
      fmap (feature-map gtf)
      [+cnts1 -cnts1] (read-mapfile map1)
      [+cnts2 -cnts2] (read-mapfile map2)
      tot1 (time (m/sum (mapv #(m/sum (vals %)) [+cnts1 -cnts1])))
      tot2 (time (m/sum (mapv #(m/sum (vals %)) [+cnts2 -cnts2])))
      avgtot (-> tot1 (+ tot2) (/ 2.0))
      cfactor1 (/ tot1 avgtot)
      cfactor2 (/ tot2 avgtot)
      hdr HDR
      st1fn #(cond (and (+cnts1 %) (-cnts1 %)) "b" (+cnts1 %) "+" :else "-")
      st2fn #(cond (and (+cnts2 %) (-cnts2 %)) "b" (+cnts2 %) "+" :else "-")
      subdelta 7000
      ]
  [(count +cnts1) (count -cnts1) (count +cnts2) (count -cnts2)])
[(+cnts1 147) (-cnts1 147) (+cnts2 147) (-cnts2 147)]

;;grep -P "\t147\t" ../Out/Rep/Maps/19F-d1504T1-1.map | more


  (->> "TCTGAATCTCCTGCTACCGACCATGAACTAACAGGTTGGATGATAAGTCTT"
       pass-0-pipe count)

  (-> "GACTACCCTCTGGACCCCAACCGTCATATAAGATGTTGGATGATAAGTCCC"
      (trim-initsq-and-adapter 9 -17)
      (seq-less-transposon "TAACAG"))


  (def tnseqs
    ["TCTGAATCTCCTGCTACCGACCATGAACTAACAGGTTGGATGATAAGTCTT"
     "TCTGAATCTCCTGCTACCTGACCATGAACTAACAGGTTGGATGATAAGTCT"
     "TCTGAATCTCCTGCTACCTTGACCATGAACTAACAGGTTGGATGATAAGTC"
     "TCTGAATCTCCTGCTACCTTTGACCATGAACTAACAGGTTGGATGATAAGT"

     "TCTGAATCTCCTGCTACCGACCATGAACTAACxGGTTGGATGATAAGTCTT"
     "TCTGAATCTCCTGCTACCGACCATGAACTAxCxGGTTGGATGATAAGTCTT"
     "TCTGAATCTCCTGCTACCTTTGACCATGAACTxACAGGTTGGATGATAAGT"
     "TCTGAATCTCCTGCTACCTTTGACCATGAACTAACAGGTTGGATGATAAGT"
     ])

(let [sq "CCTGCTACCGACCATGAACTAACAG"
      tpn "TAACAG"
      mxtpn (count tpn)
      minsq (- (count sq) mxtpn)
      idxs0 (take (- mxtpn 2)
                  (iterate (fn[[x y]] [(inc x) (dec y)])
                           [minsq mxtpn]))
      maxsqi (->> idxs0 last first)]
  [mxtpn minsq idxs0 maxsqi])

(let [tnseqs ["CCTGCTACCGACCATGAACTAACAG"
              "CCTGCTACCGACCATGAACTAACxG"
              "CCTGCTACCGACCATGAACTAxCxG"
              "CCTGCTACCTTTGACCATGAACTxA"
              "CCTGCTACCTTTGACCATGAACTAA"]]
  (map #(position-transpose-prefix % "TAACAG") tnseqs))


(let [tnseqs ["CCTGCTACCGACCATGAACTAACAG"
              "CCTGCTACCGACCATGAACTAACxG"
              "CCTGCTACCGACCATGAACTAxCxG"
              "CCTGCTACCTTTGACCATGAACTxA"
              "CCTGCTACCTTTGACCATGAACTAA"]]
  (time (dotimes [_ 200000]
          (mapv #(position-transpose-prefix % "TAACAG") tnseqs))))


(let [tnseqs ["CCTGCTACCGACCATGAACTAACAG"
              "CCTGCTACCGACCATGAACTAACxG"
              "CCTGCTACCGACCATGAACTAxCxG"
              "CCTGCTACCTTTGACCATGAACTxA"
              "CCTGCTACCTTTGACCATGAACTAA"]]
  (->> tnseqs (map #(seq-less-transposon % "TAACAG"))))


(time (dotimes [_ 10000000]
        (->>  (str/sliding-take
               12 "TACTTACCTACTXCCGCTGGTCATCCTGCGCCAATTTGATGTGTGTGGTTTTTAATTG")
              (take 3)
              (map #(it/hamming "ACTTACCTACTT" %)))))

(time (dotimes [_ 1000000]
        (->>  (str/sliding-take
               6 "TACTTACCTACTXCCGCTGGTCATCCTGCGCCAATTTGATGTGTGTGGTTTTTAATTG")
              (take 3)
              (mapv #(it/hamming "ACTTAC" %)))))

(->>  (str/sliding-take
       6 "TACTTACCTACTXCCGCTGGTCATCCTGCGCCAATTTGATGTGTGTGGTTTTTAATTG")
      (take 3)
      (map #(it/hamming "ACTTAC" %))
      (apply min) (>= 1))

(->>  (str/sliding-take
       6 "TACTTACCTACTXCCGCTGGTCATCCTGCGCCAATTTGATGTGTGTGGTTTTTAATTG")
      (take 3)
      (map #(it/hamming "ACTTAC" %))
      (apply min) (>= 1))

)
