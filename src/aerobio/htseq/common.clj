(ns iobio.htseq.common
  [:require
   [clojure.core.reducers :as r]
   [clojure.data.csv :as csv]
   [clojure.string :as cljstr]

   [aerial.fs :as fs]

   [aerial.utils.coll :refer [vfold] :as coll]
   [aerial.utils.string :as str]
   [aerial.utils.io :refer [letio] :as io]
   [aerial.utils.math.probs-stats :as p]
   [aerial.utils.math.infoth :as it]
   [aerial.utils.ds.bktrees :as bkt]

   [aerial.bio.utils.files :as bufiles]
   [aerial.bio.utils.aligners :as aln]
   [aerial.bio.utils.filters :as fil]

   [iobio.params :as pams]
   [iobio.pgmgraph :as pg]])




(defn get-sample-info [ssheet]
  (->> ssheet
       slurp csv/read-csv
       (coll/drop-until #(= (first %) "Sample_ID"))
       rest))

(defn get-exp-sample-info [csv]
  (let [recs (->> csv slurp csv/read-csv)
        x (coll/drop-until
           (fn[v] (#{"tnseq", "rnaseq", "trmseq" "wgseq"} (first v)))
           recs)
        recs (if (seq x) x (cons ["rnaseq" "noexp" "noexp"] recs))
        exp-rec [(first recs)]]
    (loop [S (rest recs)
           I [exp-rec]]
      (if (not (seq S))
        (apply concat I)
        (let [s (coll/drop-until (fn[v] (str/digits? (first v))) S)
              i (coll/take-until (fn[v] (not (str/digits? (first v)))) s)]
          (recur (coll/drop-until (fn[v] (not (str/digits? (first v)))) s)
                 (conj I i)))))))

(defn get-ncbi-xref
  [exp-samp-info]
  (->> exp-samp-info rest
       (take-while #(= 3 (count %)))))

(defn drop-ncbi-xref
  [exp-samp-info]
  (->> exp-samp-info rest
       (drop-while #(= 3 (count %)))))

(defn get-exp-type
  [exp-samp-info]
  (-> exp-samp-info ffirst keyword))


(defn ensure-dirs [& dirs]
  (reduce (fn[Res dir]
            (and Res (if (fs/directory? dir) true (fs/mkdirs dir))))
          true dirs))

(defn start-scratch-space [eid]
  (let [base (pams/get-params :scratch-base)
        scratch-dir (fs/join base eid)
        nextseq-base (pams/get-params :nextseq-base)
        expdir (fs/join nextseq-base eid)
        exp-fqdir (fs/join expdir (pams/get-params :nextseq-fqdir))
        fqs (filter #(not (str/substring? "Undetermined" %))
                    (fs/re-directory-files exp-fqdir "fastq.gz"))
        fq-otdir (fs/join scratch-dir (pams/get-params :fastq-dirname))]
    (ensure-dirs scratch-dir fq-otdir)
    (doseq [fq fqs]
      (fs/copy fq (fs/join fq-otdir (fs/basename fq))))
    eid))


(defn bcfreqs-fold
  "Fold over barcode and base frequency computations"
  [smap sz fq]
  (letio [kw (->> fq fs/basename
                  (str/split #".fastq") first (str/split #"_S")
                  first smap) ; kw is associated illumina barcode
          inf (io/open-streaming-gzip fq :in)
          rec-chunk-size 40000
          ;; 400 group => ~100 groups => 2 groups/deque on 50cpu
          partition (long (/ rec-chunk-size 100))]
    ;; ***NOTE: we are NOT collecting nt freqs any more!!
    ;;          But we keep the same shape just in case we may again
    [kw (loop [[bcM ntM] [{} {}]]
          (let [chunk (bufiles/read-fqrecs inf rec-chunk-size)]
            (if (not (seq chunk))
              [bcM ntM]
              (let [[bcm ntm]
                    (r/fold
                     partition
                     (fn([] [{} {}])
                       ([[bcM ntM] [bcm ntm]]
                        [(merge-with + bcM bcm)
                         #_(merge-with + ntM ntm) ntM]))
                     (fn[[bcm ntm] fqrec]
                       (let [sq (second fqrec)
                             bc (str/substring sq 0 sz)]
                         [(assoc bcm bc (inc (get bcm bc 0)))
                          #_(merge-with + ntm (p/freqn 1 sq)) ntm]))
                     chunk)]
                (recur [(merge-with + bcM bcm)
                        #_(merge-with + ntM ntm) ntM])))))]))

(defn bcfreqs
  [smap bcsz fq]
  (future
    (letio [kw (->> fq fs/basename
                    (str/split #".fastq") first (str/split #"_S")
                    first smap) ; kw is associated illumina barcode
            in (io/open-file fq :in)]
      (loop [fqrec (bufiles/read-fqrec in)
             m {}]
        (if (nil? (fqrec 0))
          [kw [m {}]]
          (let [sq (-> (fqrec 1)
                       (str/substring 9 -17)
                       (str/substring 0 bcsz))]
            (recur (bufiles/read-fqrec in)
                   (assoc m sq (inc (get m sq 0))))))))))

(defn bcfreqs-tnseq
  [smap bcsz fqs]
  (let [futvec (mapv (partial bcfreqs smap bcsz) fqs)]
    (mapv (fn[fut] (deref fut)) futvec)))


(defn collect-barcode-stats [eid]
  (let [nextseq-base (pams/get-params :nextseq-base)
        expdir (fs/join nextseq-base eid)
        sample-map (->> "SampleSheet.csv"
                        (fs/join expdir) get-sample-info
                        (map (fn[[nm _ bckey]] [nm bckey]))
                        (into {}))
        expinfo (->> "Exp-SampleSheet.csv" (fs/join expdir)
                     get-exp-sample-info)
        bcsz (->> expinfo drop-ncbi-xref first last count)
        exptype (get-exp-type expinfo)
        base (pams/get-params :scratch-base)
        fqbase (fs/join base eid "Fastq")
        fqs (fs/re-directory-files fqbase "*.fastq.gz")]
    (if (= exptype :tnseq)
      (->> (bcfreqs-tnseq sample-map bcsz fqs) (into {}))
      (->> fqs
           (mapv (partial bcfreqs-fold sample-map bcsz))
           (into {})))))

;;; base "/data1/NextSeq/TVOLab/AHL7L3BGXX/Stats/"
;;; (write-bcmaps "160307_NS500751_0013_AHL7L3BGXX/")
(defn write-bcmaps [eid bcmaps]
  (let [nextseq-base (pams/get-params :nextseq-base)
        expdir (fs/join nextseq-base eid)
        sample-info (get-sample-info (fs/join expdir "SampleSheet.csv"))
        base (fs/join (pams/get-params :scratch-base) eid "Stats")
        files (into {} (mapv
                        (fn[[snm _ bckey]]
                          [bckey (fs/join base (str snm ".clj"))])
                        sample-info))]
    (ensure-dirs base)
    (doseq [[k v] bcmaps]
      (let [f (files k)]
        (io/with-out-writer f
          (prn {k v}))))))

(defn read-bcmap [f]
  (clojure.edn/read-string (slurp f)))

(defn bcM [bcmaps k] (->> k bcmaps first))


(defn bc-counts [barcodes bcmaps]
  (into {} (map (fn[[k [bcM ntM]]]
                  [k (->> barcodes
                          (map #(vector %1 (bcM %1)))
                          (sort-by second >))])
                bcmaps)))

(defn nonbc-counts [bcM barcodes
                    & {:keys [cnt-cutoff mxtake]
                       :or {cnt-cutoff 1000 mxtake 70}}]
  (let [x (->> bcM (filter (fn[[k v]] (> v cnt-cutoff))) (into {})
               (#(apply dissoc % barcodes))
               (sort-by second >))]
    (if (integer? mxtake) (take mxtake x) x)))

(defn barcodes-edist-1-seqs
  [bcM barcodes]
  (let [all (map (fn[[x _]]
                   (vector x (keep #(when (= 1 (it/hamming x %)) %)
                                   barcodes)))
                 (nonbc-counts bcM barcodes :cnt-cutoff 1000 :mxtake 100))]
    (->> all (keep (fn[[x bcs]] (when (= 1 (count bcs)) [(first bcs) x])))
         (reduce (fn[M [bc v]] (assoc M bc (conj (get M bc []) v))) {}))))


(def exp-info (atom {}))
;;; "/data1/NextSeq/TVOLab/AHL7L3BGXX"
;;; "/data1/NextSeq/TVOLab/AHL7L3BGXX/Docs/SampleSheet.csv"
;;; "/data1/NextSeq/TVOLab/AHL7L3BGXX/Docs/Exp-AntibioticsRTS_KZ_SD_index.csv"
(defn init-exp-data [base ssheet exp-ssheet]
  (-> {}
      (assoc :sample-sheet
             (get-sample-info ssheet))
      ((fn[m]
         (assoc m :base base
                :refs      (pams/get-params :refdir)
                :index     (fs/join (pams/get-params :refdir) "Index")
                :samples   (fs/join base "Samples")
                :collapsed (fs/join base "Samples/Collapsed")
                :out       (fs/join base "Out")
                :bams      (fs/join base "Out/Bams")
                :fcnts     (fs/join base "Out/Fcnts")
                :cuffs     (fs/join base "Out/Cuffs")
                :diffs     (fs/join base "Out/Diffs")
                :asms      (fs/join base "Out/Asms")
                :charts    (fs/join base "Out/Charts")
                :stats     (fs/join base "Stats")
                :fastq     (fs/join base "Fastq")
                :docs      (fs/join base "Docs"))))
      ((fn[m]
         (assoc m :illumina-sample-xref
                (into {} (mapv (fn[[_ nm ibc]] [ibc nm])
                               (m :sample-sheet))))))
      ((fn[m]
         (assoc m :exp-sample-info
                (get-exp-sample-info exp-ssheet))))
      ((fn[m]
         (assoc m :exp (get-exp-type (m :exp-sample-info))
                :sample-names
                (->> (m :exp-sample-info)
                     drop-ncbi-xref
                     (map second)
                     (map #(->> % (str/split #"-")
                                (take 2) (cljstr/join "-")))
                     set))))
      ((fn[m]
         (assoc m :replicate-names
                (->> (m :exp-sample-info)
                     drop-ncbi-xref
                     (map second)
                     (group-by (fn[rnm]
                                 (->> rnm (str/split #"-")
                                      (take 2) (cljstr/join "-"))))))))
      ((fn[m]
         (assoc m :strains
                (->> (m :sample-names)
                     (mapv #(->> % (str/split #"-") first))
                     set))))
      ((fn[m]
         (assoc m :ncbi-sample-xref
                (->> (m :exp-sample-info)
                     get-ncbi-xref
                     (mapcat (fn[x] [(-> x rest vec) (-> x rest reverse vec)]))
                     (into {})))))
      ((fn[m]
         (assoc m :exp-illumina-xref
                (->> (m :exp-sample-info)
                     drop-ncbi-xref
                     (group-by (fn[[_ _ ibc]] ibc))))))
      ((fn[m]
         (assoc m :barcodes
                (->> (m :exp-illumina-xref) vals (apply concat)
                     (reduce (fn[BC v] (conj BC (last v))) #{})
                     sort vec))))
      ((fn[m]
         (assoc m :barcode-maps
                (->> (m :exp-illumina-xref)
                     (map #(vector (first %)
                                   (->> % second (map last)
                                        (map (fn[bc] (vector bc bc)))
                                        (into {}))))
                     (into {})))))
      ((fn[m]
         (assoc m :bcmaps
                (apply merge
                       (map read-bcmap
                            (fs/directory-files (m :stats) ".clj"))))))
      ((fn[m]
         (assoc m :ed1codes
                (let [bcmaps (m :bcmaps)
                      barcode-maps (m :barcode-maps)]
                  (into {} (map #(vector % (barcodes-edist-1-seqs
                                            (bcM bcmaps %)
                                            (keys (barcode-maps %))))
                                (keys bcmaps)))))))
      ((fn[m]
         (assoc m :red1codes
                (into {}
                      (mapv
                       (fn[samp-key]
                         [samp-key
                          (reduce (fn[M [k l]]
                                    (merge M (reduce (fn[m x] (assoc m x k))
                                                     {} l)))
                                  {} ((m :ed1codes) samp-key))])
                       (keys (m :bcmaps)))))))
      ))



(defn set-exp [eid]
  (let [base (fs/join (pams/get-params :scratch-base) eid)
        nextseq-base (pams/get-params :nextseq-base)
        expdir (fs/join nextseq-base eid)
        sample-sheet (fs/join expdir "SampleSheet.csv")
        exp-sample-sheet (fs/join expdir "Exp-SampleSheet.csv")]
    (swap! exp-info
           (fn[m]
             (assoc m eid
                    (init-exp-data base sample-sheet exp-sample-sheet))))))

(defn get-exp [eid] (@exp-info eid))

(defn exp-ids [] (keys @exp-info))

(defn info-ks [] (-> @exp-info first second keys))

(defn get-exp-info [eid & ks]
  (let [otks [:bams :fcnts :cuffs :diffs :asms :charts]
        rep? (coll/in :rep ks)
        ks (remove #{:rep} (filter identity ks)) ; remove any :rep or nil
        info (get-exp eid)
        xfn  (fn [k]
               (let [item (info k)]
                 (if (and rep? (coll/in k otks))
                   (let [dirs  (fs/split item)
                         ldir (last dirs)
                         dirs (-> dirs butlast vec)]
                     (apply fs/join (conj dirs "Rep" ldir)))
                   item)))]
    (if (= 1 (count ks))
      (xfn (first ks))
      (mapv #(xfn %) ks))))

(defn get-exp-files [exp-id d]
  (->> [:refs :index :samples
        :out :bams :cuffs :diffs
        :asms :charts :stats :fastq :docs]
       (mapv #(vector % ((@exp-info exp-id) %)))
       (map (fn[[n d]] [n (fs/directory-files d "")]))
       (into {})
       d))


(defn get-sample-base-dir [base]
  (if (->> base fs/basename (= "Samples"))
    base
    (fs/join base "Samples")))

(defn ensure-sample-dirs [base illumina-sample-xref]
  (let [base (get-sample-base-dir base)]
    (when-not (fs/directory? base)
      (fs/mkdir base))
    (doseq [d (vals illumina-sample-xref)]
      (let [dir (fs/join base d)]
        (when-not (fs/directory? dir)
          (fs/mkdir dir))))))

(defn get-exp-file-specs [exp-illumina-xref exp-dir ibc]
  (->>  ibc exp-illumina-xref
        (map (fn[[id nm ibc sbc]] [sbc (str nm "-" sbc ".fastq.gz")]))
        (map (fn[[sbc spec]] [sbc (fs/join exp-dir spec)]))
        (into {})))

(defn get-bc-file-specs [base exp-illumina-xref illumina-sample-xref]
  (let [base (get-sample-base-dir base)
        ibcs (keys illumina-sample-xref)
        exp-sample-dirs (->> ibcs (map illumina-sample-xref)
                             (map #(fs/join base %)))]
    (into {}
          (map (fn[exp-dir ibc]
                 [ibc (get-exp-file-specs exp-illumina-xref exp-dir ibc)])
               exp-sample-dirs
               ibcs))))


(defn get-sq-bc [sq bclen barcodes-map other-barcodes]
  (when sq
    (let [bc (str/substring sq 0 bclen)]
      (or (barcodes-map bc) (other-barcodes bc)))))

(defn pass-qcscore [qc qc-ctpt sqc%]
  (> (fil/percent-pass-qscore qc qc-ctpt) (double sqc%)))


(def SDBG (atom #{}))
;;;#{"GGATGG" "CTTAAC" "TAATTA" "ATGGAT" "AATTGC" "TGCTTA"}
(defn write-chunk-to-files [ot-fd-map recs]
  (let [file-groups (->> recs (group-by
                               (fn[[bc fqrec]]
                                 (let [fd (ot-fd-map bc)]
                                   (if fd fd
                                       (when (not (@SDBG bc))
                                         (swap! SDBG #(conj % bc))
                                         (prn :BAD-BC?! bc))))))
                         (mapv (fn[[fd rec]] [fd (mapv second rec)])))

        file-groups (filter (fn[[fd recs]] fd) file-groups)]
    (dorun
     (mapv (fn[fut] (deref fut))
           (mapv (fn[[fd recs]]
                   (future (bufiles/write-fqrecs-to-file fd recs)))
                 file-groups)))))

(defn split-barcodes
  [in-fq sqxform-fn ot-fq-map barcodes-map other-barcodes qc-ctpt sqc%]

  (letio [bclen (->> ot-fq-map keys first count long)
          inf (io/open-streaming-gzip in-fq :in)
          lines (io/read-lines inf)
          ot-fd-map (reduce (fn[M [ebc fq]]
                              (assoc M ebc (io/open-streaming-gzip fq :out)))
                            {}
                            ot-fq-map)
          rec-chunk-size 10000
          ;; 100 group => ~100 groups => 2 groups/deque on 50cpu
          partition (long (/ rec-chunk-size 100))]
    (try
      (loop [recs (bufiles/read-fqrecs inf rec-chunk-size)]
        (if (not (seq recs))
          :done
          (let [recs (r/fold
                      partition
                      (fn([] [])
                        ([V v]
                         (reduce (fn [V rec]
                                   (conj V rec))
                                 V v)))
                      (fn[V fqrec]
                        (let [[id sq aux qc] (sqxform-fn fqrec)
                              bc (get-sq-bc sq bclen
                                            barcodes-map
                                            other-barcodes)]
                          (if bc
                            (if (pass-qcscore qc qc-ctpt sqc%)
                              (let [len (count sq)
                                    sq (str/substring sq bclen len)
                                    qc (str/substring qc bclen len)]
                                (conj V [bc [id sq aux qc]]))
                              V)
                            V)))
                      recs)]
            (write-chunk-to-files ot-fd-map recs)
            (recur (bufiles/read-fqrecs inf rec-chunk-size)))))
      (finally
        (doseq [fd (vals ot-fd-map)]
          (. fd close))))))


(defn split-filter-fastqs
  [eid sqxform-fn & {:keys [baseqc% sqc%]
                     :or {baseqc% 0.96 sqc% 0.97}}]

  (let [base (get-exp-info eid :base)
        exp-illumina-xref (get-exp-info eid :exp-illumina-xref)
        illumina-sample-xref (get-exp-info eid :illumina-sample-xref)
        barcode-maps (get-exp-info eid :barcode-maps)
        red1codes (get-exp-info eid :red1codes)
        [qc-ctpt _] (fil/qcscore-min-entropy baseqc% 0.9 10)
        bc-file-specs (get-bc-file-specs
                       base exp-illumina-xref illumina-sample-xref)
        ifastqs (fs/directory-files (fs/join base "Fastq") "fastq.gz")
        sample-illumina-xref (clojure.set/map-invert illumina-sample-xref)
        sample-ifq-xref (reduce (fn[M fq]
                                  (let [samp (->> fq fs/basename
                                                  (str/split #"\.") first
                                                  (str/split #"_") first)]
                                    (assoc M samp fq)))
                                {} ifastqs)]
    (ensure-sample-dirs base illumina-sample-xref)
    (doseq [samp (sort (keys sample-ifq-xref))]
      (let [ibc (sample-illumina-xref samp)
            sid ibc]
        (split-barcodes (sample-ifq-xref samp)
                        sqxform-fn
                        (bc-file-specs ibc)
                        (barcode-maps ibc)
                        (red1codes sid)
                        qc-ctpt sqc%)))
    :success))




(defn fqz-name->sample-name
  [fqz]
  (->> fqz fs/basename (str/split #"-") (take 2) (cljstr/join "-")))

(defn get-all-replicate-fqzs [eid]
  (let [base (get-exp-info eid :base)
        sample-dirs (fs/directory-files (get-exp-info eid :samples) "")
        fqzs (mapcat #(fs/directory-files % "gz") sample-dirs)
        by-samples (group-by fqz-name->sample-name fqzs)]
    (into {} (map #(vector % (by-samples %))
                  (get-exp-info eid :sample-names)))))

(defn get-replicate-fqzs [eid repname rep?]
  (let [fqzmap (get-all-replicate-fqzs eid)
        rnm-bits (str/split #"-" repname)
        fqzs (fqzmap (cljstr/join "-" (coll/takev 2 rnm-bits)))]
    (if (< (count rnm-bits) 3)
      fqzs
      (->> fqzs
           (keep-indexed (fn[idx itm] (when (str/substring? repname itm) idx)))
           first fqzs vector))))

(defn replicate-name->strain-name [eid rnm]
  ((get-exp-info eid :ncbi-sample-xref) (->> rnm (str/split #"-") first)))






(comment
  (def eid "160930_161002_AHK72HBGXY_AHK7YKBGXY")
  (def neid "161108_161109_AH2WWGBGX2_AH2WLJBGX2")
  (def teid "160804_NS500751_0017_AHF2KLBGXY")

  (set-exp "160307_NS500751_0013_AHL7L3BGXX")
  (set-exp "160930_161002_AHK72HBGXY_AHK7YKBGXY")
  (set-exp "161108_161109_AH2WWGBGX2_AH2WLJBGX2")
  (set-exp teid)

  (get-exp "160307_NS500751_0013_AHL7L3BGXX")
  ((get-exp "160307_NS500751_0013_AHL7L3BGXX") :base)
  (info-ks)
  (exp-ids)
  (get-exp-info "160307_NS500751_0013_AHL7L3BGXX" :base :sample-sheet :bcmaps)

  (->> (aerial.utils.math.combinatorics/combins
        2 (get-exp-info teid :barcodes))
       (map #(apply it/hamming %))
       sort)

  (def eds1 (->> (get-exp-info teid :ed1codes) first second vals))
  (def bcs1 (->> (get-exp-info teid :ed1codes) first second keys))
  (for [bc bcs1]
    [bc (for [v eds1]
          (mapv #(it/levenshtein bc %) v))])
  )
