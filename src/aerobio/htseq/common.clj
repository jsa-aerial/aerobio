(ns aerobio.htseq.common
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

   [aerial.bio.utils.files :as bufiles]
   [aerial.bio.utils.filters :as fil]

   [aerobio.params :as pams]
   [aerobio.pgmgraph :as pg]])




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
  [smap fq]
  (letio [kw (->> fq fs/basename
                  (str/split #".fastq") first (str/split #"_S")
                  first smap)
          inf (io/open-streaming-gzip fq :in)
          rec-chunk-size 10000
          ;; 100 group => ~100 groups => 2 groups/deque on 50cpu
          partition (long (/ rec-chunk-size 100))]
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
                         (merge-with + ntM ntm)]))
                     (fn[[bcm ntm] fqrec]
                       (let [sq (second fqrec)
                             bc (str/substring sq 0 7)]
                         [(assoc bcm bc (inc (get bcm bc 0)))
                          (merge-with + ntm (p/freqn 1 sq))]))
                     chunk)]
                (recur [(merge-with + bcM bcm) (merge-with + ntM ntm)])))))]))


(defn get-sample-info [ssheet]
  (->> ssheet
       slurp csv/read-csv
       (coll/drop-until #(= (first %) "Sample_ID"))
       rest))

(defn collect-barcode-stats [eid]
  (let [nextseq-base (pams/get-params :nextseq-base)
        expdir (fs/join nextseq-base eid)
        sample-map (->> "SampleSheet.csv"
                        (fs/join expdir) get-sample-info
                        (map (fn[[nm _ bckey]] [nm bckey]))
                        (into {}))
        base (pams/get-params :scratch-base)
        fqbase (fs/join base eid "Fastq")]
    (->> (fs/re-directory-files
          fqbase "*.fastq.gz")
         (mapv (partial bcfreqs-fold sample-map))
         (into {}))))

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


(defn get-exp-sample-info [csv]
  (loop [S (->> csv slurp csv/read-csv)
         I []]
    (if (not (seq S))
      (apply concat I)
      (let [s (coll/drop-until (fn[v] (str/digits? (first v))) S)
            i (coll/take-until (fn[v] (not (str/digits? (first v)))) s)]
        (recur (coll/drop-until (fn[v] (not (str/digits? (first v)))) s)
               (conj I i))))))


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
                :refs    (pams/get-params :refdir)
                :index   (fs/join (pams/get-params :refdir) "Index")
                :samples (fs/join base "Samples")
                :out     (fs/join base "Out")
                :bams    (fs/join base "Out/Bams")
                :cuffs   (fs/join base "Out/Cuffs")
                :diffs   (fs/join base "Out/Diffs")
                :asms    (fs/join base "Out/Asms")
                :charts  (fs/join base "Out/Charts")
                :stats   (fs/join base "Stats")
                :fastq   (fs/join base "Fastq")
                :docs    (fs/join base "Docs"))))
      ((fn[m]
         (assoc m :illumina-sample-xref
                (into {} (mapv (fn[[_ nm ibc]] [ibc nm])
                               (m :sample-sheet))))))
      ((fn[m]
         (assoc m :exp-sample-info
                (get-exp-sample-info exp-ssheet))))
      ((fn[m]
         (assoc m :sample-names
                (->> (m :exp-sample-info)
                     (drop-while #(= 3 (count %)))
                     (map second)
                     (map #(->> % (str/split #"-")
                                (take 2) (cljstr/join "-")))
                     set))))
      ((fn[m]
         (assoc m :replicate-names
                (->> (m :exp-sample-info)
                     (drop-while #(= 3(count %)))
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
                     (take-while #(= 3 (count %)))
                     (mapcat (fn[x] [(-> x rest vec) (-> x rest reverse vec)]))
                     (into {})))))
      ((fn[m]
         (assoc m :exp-illumina-xref
                (group-by (fn[[_ _ ibc]] ibc) (m :exp-sample-info)))))
      ((fn[m]
         (assoc m :barcodes
                (->> (m :exp-illumina-xref) vals (apply concat)
                     (reduce (fn[BC v] (conj BC (last v))) #{})
                     sort vec))))
      ((fn[m]
         (assoc m :barcodes-map
                (reduce (fn[M bc] (assoc M bc bc)) {} (m :barcodes)))))
      ((fn[m]
         (assoc m :bcmaps
                (apply merge
                       (map read-bcmap
                            (fs/directory-files (m :stats) ".clj"))))))
      ((fn[m]
         (assoc m :ed1codes
                (let [bcmaps (m :bcmaps)]
                  (into {} (map #(vector % (barcodes-edist-1-seqs
                                            (bcM bcmaps %)
                                            (m :barcodes)))
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
  (let [otks [:bams :cuffs :diffs :asms :charts]
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


(defn get-sq-bc [sq barcodes-map other-barcodes]
  (let [bc (str/substring sq 0 7)]
    (or (barcodes-map bc) (other-barcodes bc))))

(defn pass-qcscore [qc qc-ctpt sqc%]
  (> (fil/percent-pass-qscore qc qc-ctpt) (double sqc%)))


(defn write-chunk-to-files [ot-fd-map recs]
  (let [file-groups (->> recs (group-by (fn[[bc fqrec]] (ot-fd-map bc)))
                         (mapv (fn[[fd rec]] [fd (mapv second rec)])))]
    (dorun
     (mapv (fn[fut] (deref fut))
           (mapv (fn[[fd recs]]
                   (future (bufiles/write-fqrecs-to-file fd recs)))
                 file-groups)))))

(defn split-barcodes [in-fq ot-fq-map barcodes-map other-barcodes qc-ctpt sqc%]
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
                      (fn[V [id sq aux qc :as rec]]
                        (let [bc (get-sq-bc sq barcodes-map other-barcodes)]
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


(defn split-filter-fastqs [eid & {:keys [baseqc% sqc%]
                               :or {baseqc% 0.96 sqc% 0.97}}]
  (let [base (get-exp-info eid :base)
        exp-illumina-xref (get-exp-info eid :exp-illumina-xref)
        illumina-sample-xref (get-exp-info eid :illumina-sample-xref)
        barcodes-map (get-exp-info eid :barcodes-map)
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
                        (bc-file-specs ibc)
                        barcodes-map
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


