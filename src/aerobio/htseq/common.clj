;;--------------------------------------------------------------------------;;
;;                                                                          ;;
;;               A E R O B I O . H T S E Q . C O M M O N                    ;;
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

(ns aerobio.htseq.common
  [:require
   [clojure.core.reducers :as r]
   [clojure.data.csv :as csv]
   [clojure.string :as cljstr]

   [tech.v3.dataset :as ds]
   [tablecloth.api :as tc]

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

   [aerobio.params :as pams]
   [aerobio.htseq.paired :refer [demux-R2-samp demux-R2-samp-no-replicates]]
   [aerobio.pgmgraph :as pg]])




(defn instrument-make [eid]
  (let [base (pams/get-params :exp-base)
        expdir (fs/join base eid)
        elembio? (-> expdir (fs/join "RunManifest.csv") fs/exists?)
        illum?   (-> expdir (fs/join "SampleSheet.csv") fs/exists?)]
    (cond elembio? :elembio
          illum?   :illum
          :else    :NA)))


(declare get-exp-info
         get-exp)


(defn get-instrument-make [eid]
  (or (get-exp-info eid :instrument-make)
      (instrument-make eid)))


;;; (ns-unmap 'aerobio.htseq.common 'get-sample-data)
(defmulti get-sample-data
  "Get base sample data, as determined by instrument make `instrument-make`"
  {:arglists '([eid])}
  (fn [eid] (get-instrument-make eid)))


(defn read-sample-data [eid marker filename]
  (let [sheet (fs/join (pams/get-params :exp-base) eid filename)]
    (-> sheet
        (tc/dataset {:header-row? false})
        (tc/rows)
        (->> (coll/dropv-until #(= marker (first %)))))))

(defmethod get-sample-data :illum
  [eid]
  (let [filename "SampleSheet.csv"
        marker "Sample_ID"]
    (read-sample-data eid marker filename)))

(defmethod get-sample-data :elembio
  [eid]
  (let [filename "RunManifest.csv"
        marker "SampleName"]
    (read-sample-data eid marker filename)))


;;; (ns-unmap 'aerobio.htseq.common 'get-sample-info)
(defmulti get-sample-info
  "Get sample information, as determined by instrument make `instrument-make`"
  {:arglists '([eid])}
  (fn [eid] (get-instrument-make eid)))

(defmethod get-sample-info :illum
  [eid]
  (->> eid get-sample-data  rest
      ;; i7 and i5 are treated as single (unique) "Illumina bc" in
      ;; Aerobio processing.  NOTE this is *downstream* of bcl2fastq
      ;; or bcl-convert
      (map (fn[[id nm i7bc i5bc]] [id nm (str i7bc i5bc)]))))

(defmethod get-sample-info :elembio
  [eid]
  (->> eid get-sample-data rest
      ;; i7 and i5 are treated as single (unique) "BC" in Aerobio
      ;; processing.  Additionally, in ElemBio land, the sequencer can
      ;; be configured so that a given sample will be contained in
      ;; only one lane.  In this case, the combined i7&i5 are not
      ;; enough to give a unique "BC" and an extra 'fake base' needs
      ;; to be appended.  Also for ElemBio, the PhiX 'samples' are
      ;; explicitly listed in Sample data section.  These need to be
      ;; removed. NOTE this is *downstream* of bases2fastq
      (map (fn[[nm i7bc i5bc lane]]
             (let [fakebase (case lane
                              "1" "A"
                              "2" "G"
                              nil)]
               [nm (str i7bc i5bc fakebase)])))
      (remove (fn[x] (-> x first (= "PhiX"))))))


(defn get-sample-info-colkws [eid]
  (let [make (get-exp-info eid :instrument-make)]
    (if (= make :illum) [:sid :snm :index] [:snm :index])))


(defn term-seq-adjust
  [secmap]
  (let [run-xref (->> secmap :run-xref (mapv #(coll/takev 3 %))
                      (mapv (fn[[_ rt bc]]
                              [bc [(keyword (cljstr/lower-case rt)) rt]]))
                      (into {}))
        bc-xref (->> secmap :bc-xref
                     (mapv (fn[[n nm ibc sbc]]
                             (let [rtnm (second (get run-xref ibc [:na ""]))
                                   [strain cond lib] (str/split #"-" nm)]
                               [n (cljstr/join "-" [strain (str rtnm cond) lib])
                                ibc sbc]))))]
    (assoc secmap :run-xref run-xref :bc-xref bc-xref)))

(defn get-exp-sample-info [csv]
  (let [recs #_(->> csv slurp csv/read-csv)
        (-> csv (tc/dataset {:header-row? false :parser-fn :string})
            (tc/replace-missing :all :value "")
            (tc/rows) vec)
        x (coll/dropv-until
           (fn[v] (#{"tnseq", "rnaseq", "dual-rnaseq"
                    "termseq" "wgseq"} (first v)))
           recs)
        recs (if (seq x) x (cons ["rnaseq" "noexp" "noexp"] recs))
        exp-rec [(coll/takev 5 (first recs))]] ; ensure 3 fields
    (loop [S (rest recs)
           I [exp-rec]]
      (if (not (seq S))
        (let [sections I
              exp (-> sections first ffirst keyword)
              ks [:exp-rec :ncbi-xref :run-xref :bc-xref]
              ks (if (= exp :termseq) ks (remove #(= % :run-xref) ks))
              secmap (zipmap ks sections)
              ;; The next bit (V2.4.0) accounts for paired end reads
              ;; where i7 is not unique.  The old assumption that it
              ;; was unique is just plain wrong.  However, we don't
              ;; use nor need the separate indices for any processing
              ;; - that is already done by bcl2fastq or bcl-convert.
              ;; We just need a unique "Illumina bc" for xrefing with
              ;; experiment bc. So, we just concat when there is both
              ;; i7 and i5.
              bcxref (secmap :bc-xref)
              bcxref (->> bcxref
                          (mapv (fn[v]
                                  (let [[n orc & bcs] v
                                        bcs (filter #(not= % "") bcs)]
                                    (concat
                                     [n orc]
                                     (if (= (count bcs) 2)
                                       bcs
                                       (coll/concatv
                                        [(str (first bcs) (second bcs))]
                                        (drop 2 bcs))))))))
              secmap (assoc secmap :bc-xref bcxref)
              secmap (if (not= exp :termseq)
                       secmap
                       (term-seq-adjust secmap))]
          secmap)
        (let [s (coll/dropv-until (fn[v] (str/digits? (first v))) S)
              i (coll/takev-until (fn[v] (not (str/digits? (first v)))) s)
              ;; ensure 3 fields for NCBI xref
              i (if (= 1 (count I)) (mapv #(coll/takev 3 %) i) i)]
          (recur (coll/drop-until (fn[v] (not (str/digits? (first v)))) s)
                 (conj I i)))))))


(defn base-singleindex? [eid]
  (if (get-exp eid)
    (get-exp-info eid :single-index)
    (-> (pams/get-params :exp-base)
        (fs/join eid "Exp-SampleSheet.csv")
        get-exp-sample-info
        :exp-rec first
        (->> (into #{}))
        (#(% "single-index")))))

(def singleindex? (memoize base-singleindex?))


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
  (-> exp-samp-info :exp-rec ffirst keyword))


(defn ensure-dirs [& dirs]
  (reduce (fn[Res dir]
            (and Res (if (fs/directory? dir) true (fs/mkdirs dir))))
          true dirs))


(defmulti get-fqs
  "Get fastq filespecs to move to output area"
  {:arglists '([eid])}
  (fn [eid] (get-instrument-make eid)))

(defmethod get-fqs :illum
  [eid]
  (let [exp-base (pams/get-params :exp-base)
        expdir (fs/join exp-base eid)
        exp-fqdir (fs/join expdir (pams/get-params :nextseq-fqdir))]
    (filter #(not (str/substring? "Undetermined" %))
            (fs/re-directory-files exp-fqdir "fastq.gz"))))

(defmethod get-fqs :elembio
  [eid]
  (let [exp-base (pams/get-params :exp-base)
        expdir (fs/join exp-base eid)
        exp-fqdir (fs/join expdir (pams/get-params :elembio-fqdir))]
    (filter #(not (or (str/substring? "Unassigned" %)
                      (str/substring? "PhiX" %)))
            (fs/re-directory-files exp-fqdir "fastq.gz"))))


(defn start-scratch-space [eid]
  (let [base (pams/get-params :scratch-base)
        scratch-dir (fs/join base eid)
        fqs (get-fqs eid)
        fq-otdir (fs/join scratch-dir (pams/get-params :fastq-dirname))]
    (ensure-dirs scratch-dir fq-otdir)
    (doseq [fq fqs]
      (fs/copy fq (fs/join fq-otdir (fs/basename fq))))
    eid))


(defn bcfreqs-fold
  "Fold over barcode and base frequency computations"
  [smap sz fq]
  (letio [kw (->> fq fs/basename
                  (str/split #".fastq") first (str/split #"_")
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
  (pg/future+
    (letio [kw (->> fq fs/basename
                    (str/split #".fastq") first (str/split #"_")
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
  ;; Single index experiment reads are not multiplexed across illumina
  ;; samples!
  (when (not (singleindex? eid))
    (let [exp-base (pams/get-params :exp-base)
          expdir (fs/join exp-base eid)
          sample-map (->> eid  get-sample-info (into {}))
          expinfo (->> "Exp-SampleSheet.csv" (fs/join expdir)
                       get-exp-sample-info)
          bcsz (->> expinfo :bc-xref first last count)
          exptype (get-exp-type expinfo)
          base (pams/get-params :scratch-base)
          fqbase (get-exp-info  eid :fastq)
          fqs (fs/re-directory-files fqbase "*.fastq.gz")]
      (if (= exptype :tnseq)
        (->> (bcfreqs-tnseq sample-map bcsz fqs) (into {}))
        (->> fqs
             (mapv (partial bcfreqs-fold sample-map bcsz))
             (into {}))))))

;;; base "/data1/NextSeq/TVOLab/AHL7L3BGXX/Stats/"
;;; (write-bcmaps "160307_NS500751_0013_AHL7L3BGXX/")
(defn write-bcmaps [eid bcmaps]
  ;; Single index experiment reads are not multiplexed across illumina
  ;; samples!
  (when (not (singleindex? eid))
    (let [sample-info (get-sample-info eid)
          base (fs/join (pams/get-params :scratch-base) eid "Stats")
          files (->> sample-info
                     (mapv (fn[[snm bckey]]
                             [bckey (fs/join base (str snm ".clj"))]))
                     (into {}))]
      (ensure-dirs base)
      (doseq [[k v] bcmaps]
        (let [f (files k)]
          (io/with-out-writer f
            (prn {k v})))))))

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
                       :or {cnt-cutoff 1000}}]
  (let [x (->> bcM (filter (fn[[k v]] (> v cnt-cutoff))) (into {})
               (#(apply dissoc % barcodes))
               (sort-by second >))]
    (if (integer? mxtake) (take mxtake x) x)))

(defn barcodes-edist-1-seqs
  [bcM barcodes]
  (let [mx (if (< (count (first barcodes)) 8) nil 500)
        all (map (fn[[x _]]
                   (vector x (keep #(when (= 1 (it/hamming x %)) %)
                                   barcodes)))
                 (nonbc-counts bcM barcodes :cnt-cutoff 1000 :mxtake mx))]
    (->> all (keep (fn[[x bcs]] (when (= 1 (count bcs)) [(first bcs) x])))
         (reduce (fn[M [bc v]] (assoc M bc (conj (get M bc []) v))) {}))))


(defn get-cmds-args [cfgfile]
  (if (fs/exists? cfgfile)
    (-> cfgfile slurp read-string)
    {}))


(def exp-info (atom {}))

(defn init-exp-data [eid]
  (let [instrmake (instrument-make eid)
        base (fs/join (pams/get-params :scratch-base) eid)
        exp-base (pams/get-params :exp-base)
        expdir (fs/join exp-base eid)
        exp-ssheet (fs/join expdir "Exp-SampleSheet.csv")
        cfgfile (fs/join expdir "cmd.config")]
    (-> {}
        (assoc :sample-sheet (get-sample-info eid))
        ((fn[m]
           (assoc m :base base
                  :instrument-make instrmake
                  :cmdsargs  (get-cmds-args cfgfile)
                  :refs      (pams/get-params :refdir)
                  :index     (fs/join (pams/get-params :refdir) "Index")
                  :bt1index  (fs/join (pams/get-params :refdir) "BT1Index")
                  :starindex (fs/join (pams/get-params :refdir) "STARindex")
                  :samples   (fs/join base "Samples")
                  :collapsed (fs/join base "Samples/Collapsed")
                  :out       (fs/join base "Out")
                  :bams      (fs/join base "Out/Bams")
                  :star      (fs/join base "Out/STAR")
                  :fcnts     (fs/join base "Out/Fcnts")
                  :maps      (fs/join base "Out/Maps")
                  :fit       (fs/join base "Out/Fitness")
                  :aggrs     (fs/join base "Out/Aggrs")
                  :charts    (fs/join base "Out/DGE")
                  :stats     (fs/join base "Stats")
                  :fastq     (fs/join base "Fastq")
                  :docs      (fs/join base "Docs"))))
        ((fn[m]
           (assoc m :illumina-sample-xref
                  (into {} (mapv (fn[[nm ibc]] [ibc nm])
                                 (m :sample-sheet))))))
        ((fn[m]
           (assoc m :exp-sample-info
                  (get-exp-sample-info exp-ssheet))))
        ((fn[m]
           (assoc m :run-xref
                  (->> (m :exp-sample-info) :run-xref))))
        ((fn[m]
           (assoc m :single-index
                  (->> (m :exp-sample-info) :exp-rec
                       first (into #{}) (#(% "single-index"))))))
        ((fn[m]
           (assoc m :exp (get-exp-type (m :exp-sample-info))
                  :sample-names
                  (->> (m :exp-sample-info)
                       :bc-xref
                       (map second)
                       (map #(->> % (str/split #"-")
                                  (take 2) (cljstr/join "-")))
                       set))))
        ((fn[m]
           (assoc m :replicate-names
                  (->> (m :exp-sample-info)
                       :bc-xref
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
                       :ncbi-xref
                       (mapcat (fn[x] [(-> x rest vec) (-> x rest reverse vec)]))
                       (into {})))))
        ((fn[m]
           (assoc m :exp-illumina-xref
                  (->> (m :exp-sample-info)
                       :bc-xref
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
        )))

(defn set-exp [eid]
  (swap! exp-info
         (fn[m]
           (assoc m eid (init-exp-data eid)))))

(defn del-exp [eid]
  (swap! exp-info (fn[m] (dissoc m eid)))
  (keys @exp-info))

(defn get-exp [eid] (@exp-info eid))

(defn exp-ids [] (keys @exp-info))

(defn info-ks [] (-> @exp-info first second keys))

(defn get-exp-info [eid & ks]
  (when (contains? @exp-info eid)
    (let [otks #{:bams :star :fcnts :maps :fit :aggrs :charts}
          rep? (coll/in :rep ks)
          ks (remove #{:rep} (filter identity ks)) ; remove any :rep or nil
          info (get-exp eid)
          xfn  (fn [k]
                 (let [item (info k)]
                   (if (and rep? (otks k))
                     (let [dirs  (fs/split item)
                           ldir (last dirs)
                           dirs (-> dirs butlast vec)]
                       (apply fs/join (conj dirs "Rep" ldir)))
                     item)))]
      (if (= 1 (count ks))
        (xfn (first ks))
        (mapv #(xfn %) ks)))))

(defn get-exp-files [exp-id d]
  (->> [:refs :index :samples
        :out :bams :charts :stats :fastq :docs]
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

(defn get-exp-file-specs
  [exp-illumina-xref exp-dir ibc ftype]
  (->>  ibc exp-illumina-xref
        (map (fn[[id nm ibc sbc]] [sbc (str nm "-" sbc "-R1" ftype)]))
        (map (fn[[sbc spec]] [sbc (fs/join exp-dir spec)]))
        (into {})))

(defn get-bc-file-specs
  [base exp-illumina-xref illumina-sample-xref
   & {:keys [ftype]
      :or {ftype ".fastq.gz"}}]
  (let [base (get-sample-base-dir base)
        ibcs (keys illumina-sample-xref)
        exp-sample-dirs (->> ibcs (map illumina-sample-xref)
                             (map #(fs/join base %)))]
    (into {}
          (map (fn[exp-dir ibc]
                 [ibc (get-exp-file-specs exp-illumina-xref exp-dir ibc ftype)])
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
                   (pg/future+ (bufiles/write-fqrecs fd recs)))
                 file-groups)))))

(defn split-barcodes
  [in-fq sqxform-fn ot-fq-map
   no-barcodes? barcodes-map other-barcodes
   qc-ctpt sqc%]

  (letio [bclen (->> ot-fq-map keys first count long)
          inf (io/open-streaming-gzip in-fq :in)
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
          (let [no-barcodes-bcs (keys ot-fq-map)
                recs (r/fold
                      partition
                      (fn([] [])
                        ([V v]
                         (reduce (fn [V rec]
                                   (conj V rec))
                                 V v)))
                      (fn[V fqrec]
                        (let [[id sq aux qc] (sqxform-fn fqrec)]

                          (if no-barcodes?
                            ;; typical in vivo no replicates branch
                            (if (pass-qcscore qc qc-ctpt sqc%)
                              (reduce (fn[V bc]
                                        (conj V [bc [id sq aux qc]]))
                                      V no-barcodes-bcs)
                              V)

                            ;; This is the usual branch (in vitro replicate)
                            (let [bc (get-sq-bc sq bclen
                                                barcodes-map
                                                other-barcodes)]
                              (if bc
                                (if (pass-qcscore qc qc-ctpt sqc%)
                                  (let [len (count sq)
                                        sq (str/substring sq bclen len)
                                        qc (str/substring qc bclen len)]
                                    (conj V [bc [id sq aux qc]]))
                                  V)
                                V)))))
                      recs)]
            (write-chunk-to-files ot-fd-map recs)
            (recur (bufiles/read-fqrecs inf rec-chunk-size)))))
      (finally
        (doseq [fd (vals ot-fd-map)]
          (. fd close))))))


(defn link-hack [R1infq R1otfqs]
  (doseq [R1otfq R1otfqs]
    (pg/ln "-s" R1infq R1otfq)))

(defn split-filter-fastqs
  [eid sqxform-fn & {:keys [baseqc% sqc% no-barcodes?]
                     :or {baseqc% 0.96 sqc% 0.97 no-barcodes? false}}]

  (let [no-barcodes? (get-exp-info eid :single-index)
        base (get-exp-info eid :base)
        exp-illumina-xref (get-exp-info eid :exp-illumina-xref)
        illumina-sample-xref (get-exp-info eid :illumina-sample-xref)
        barcode-maps (get-exp-info eid :barcode-maps)
        red1codes (get-exp-info eid :red1codes)
        [qc-ctpt _] (fil/qcscore-min-entropy baseqc% 0.9 10)
        bc-file-specs (get-bc-file-specs
                       base exp-illumina-xref illumina-sample-xref)
        ifastqs (->> (fs/directory-files (get-exp-info eid :fastq) "fastq.gz")
                     (group-by #(->> % fs/basename
                                     (re-find #"_R[1-2]")
                                     (str/drop 1)
                                     keyword)))
        sample-illumina-xref (clojure.set/map-invert illumina-sample-xref)
        sample-ifq-xref (reduce (fn[M fq]
                                  (let [samp (->> fq fs/basename
                                                  (str/split #"\.") first
                                                  (str/split #"_") first)]
                                    (assoc M samp fq)))
                                {} (ifastqs :R1))]
    (ensure-sample-dirs base illumina-sample-xref)

    ;; QC filter, demultiplex R1 reads
    ;;
    (doseq [samp (sort (keys sample-ifq-xref))]
      (let [ibc (sample-illumina-xref samp)
            sid ibc]
        (when (barcode-maps ibc)
          (if no-barcodes?
            (link-hack (sample-ifq-xref samp)
                       (vals (bc-file-specs ibc)))

            (split-barcodes (sample-ifq-xref samp)
                            sqxform-fn
                            (bc-file-specs ibc)
                            no-barcodes?
                            (barcode-maps ibc)
                            (red1codes sid)
                            qc-ctpt sqc%)))))

    ;; If we have paired reads (R2 has data) demultiplex R2s
    ;;
    (if no-barcodes?
      (doseq [samp (vals illumina-sample-xref)]
        (demux-R2-samp-no-replicates eid samp))
      (when (ifastqs :R2)
        (let [futs (mapv #(pg/future+ (demux-R2-samp eid %))
                         (vals illumina-sample-xref))]
          (mapv deref futs))))

    :success))



(defn fqz-name->sample-name
  [fqz]
  (->> fqz fs/basename (str/split #"-") (take 2) (cljstr/join "-")))

(defn get-all-replicate-fqzs [eid]
  (let [base (get-exp-info eid :base)
        sample-dirs (fs/directory-files (get-exp-info eid :samples) "")
        fqzs (mapcat #(fs/directory-files % "fastq.gz") sample-dirs)
        fqzs (if (seq fqzs)
               fqzs
               (mapcat #(fs/directory-files % "fastq") sample-dirs))
        by-samples (group-by fqz-name->sample-name fqzs)]
    (into {} (map #(vector % (by-samples %))
                  (get-exp-info eid :sample-names)))))

(defn get-replicate-fqzs [eid repname rep?]
  (let [fqzmap (get-all-replicate-fqzs eid)
        rnm-bits (str/split #"-" repname)
        fqzs (fqzmap (cljstr/join "-" (coll/takev 2 rnm-bits)))]
    (assert (seq fqzs)
            (format "%s : There are no fastq files for %s" eid repname))
    (sort
     (if (< (count rnm-bits) 3)
       fqzs
       (->> fqzs
            (keep-indexed (fn[idx itm] (when (str/substring? repname itm) idx)))
            (mapv fqzs))))))

(defn get-paired-fqs [eid repname repk]
  (->> (get-replicate-fqzs eid repname repk)
       (group-by (fn[fq] (if (re-find #"-R2\." fq) :R2 :R1)))
       (map (fn[[k v]] [k (cljstr/join "," v)]))
       (into {})))


(defn replicate-name->strain-name [eid rnm]
  ((get-exp-info eid :ncbi-sample-xref) (->> rnm (str/split #"-") first)))



(defn flow-program
  ""
  [cfg get-toolinfo & {:keys [run prn]}]
  (assert (not (and run prn)) "FLOW-PROGRAM, run and prn mutually exclusive")
  (let [cfg (-> cfg (pg/config-pgm-graph-nodes get-toolinfo nil nil)
                pg/config-pgm-graph)]
    (cond
      run (->> cfg pg/make-flow-graph pg/run-flow-program)
      prn (->> cfg clojure.pprint/pprint)
      :else cfg)))

(defmacro safe-deref
  "Deref future fut ensuring against throwing an 'errant' exception due
  to being canceled"
  [fut]
  `(try
     (deref ~fut)
     (catch Error e#
       {:error (type e#), :msg ((Throwable->map e#) :cause)})
     (catch Exception e#
       {:error (type e#), :msg ((Throwable->map e#) :cause)})))

(defn job-flow-node-results
  "Walk a _running_ program flow graph obtaining the end result of each
  node. `futs-vec' is a vector of running (possibly finished) future
  tasks."
  [futs-vec status-atom]
  (reduce (fn[abort? fut]
            (when abort? (future-cancel fut))
            (let [res (safe-deref fut)
                  error? (and (map? res) (res :error))
                  msg (if error? (res :msg) (-> res :request :msg))]
              (swap! status-atom #(assoc % :done (conj (% :done) msg)))
              (if error?
                true
                false)))
          false futs-vec))


(defn run-phase-0
  [eid recipient get-toolinfo template status-atom]
  (let [ph0 template
        cfg (-> (assoc-in ph0 [:nodes :ph0 :args] [eid recipient])
                (pg/config-pgm-graph-nodes get-toolinfo nil nil)
                pg/config-pgm-graph)
        ;;_ (clojure.pprint/pprint cfg)
        futs-vec (->> cfg pg/make-flow-graph pg/run-flow-program)]
    (job-flow-node-results futs-vec status-atom)
    (@status-atom :done)))


;;;(ns-unmap 'aerobio.htseq.common 'get-phase-1-args)
(defmulti
  ^{:doc "Each experiment type has its own argument constructor, and this
 multimethod will dispatch accordingly to get the specific versions."
    :arglists '([exptype & args])}
  get-phase-1-args
  (fn[exptype & args] exptype))

(defn run-phase-1
  [eid recipient get-toolinfo template status-atom & {:keys [repk]}]
  (let [exp (get-exp-info eid :exp)
        phase1-job-template template
        bt (if (-> template (get-in [:nodes :ph1 :name])
                   (cljstr/includes? "bowtie1"))
             :bt1 :bt2)
        star (-> template (get-in [:nodes :ph1 :name])
                 (cljstr/includes? "star"))
        paired (-> template (get-in [:nodes :ph1 :name])
                 (cljstr/includes? "paired"))
        sample-names (get-exp-info eid :sample-names)
        sample-names (if repk
                       (mapcat #((get-exp-info eid :replicate-names) %)
                               (get-exp-info eid :sample-names))
                       sample-names)]
    (swap! status-atom assoc :DONE [])
    (loop [tuples (partition-all 2 sample-names)
           abort? false]
      (if (or (empty? tuples) abort?)
        abort?
        (let [tuple (first tuples)
              futs-vecs
              (mapv
               (fn[snm]
                 (let [job (assoc-in phase1-job-template [:nodes :ph1 :args]
                                     (get-phase-1-args
                                      exp eid snm :repk repk
                                      :bowtie bt :star star :paired paired))
                       cfg (-> job
                               (pg/config-pgm-graph-nodes get-toolinfo nil nil)
                               pg/config-pgm-graph)]
                   #_(clojure.pprint/pprint cfg)
                   (->> cfg pg/make-flow-graph pg/run-flow-program)))
               tuple)]
          (recur (rest tuples)
                 (coll/reducem
                  (fn[replicate futs]
                    (let [abort? (job-flow-node-results futs status-atom)
                          flow-results (@status-atom :done)]
                      (swap! status-atom #(assoc % :done []))
                      (swap! status-atom
                             #(assoc % :DONE
                                     (conj (% :DONE)
                                           [replicate (if abort?
                                                        flow-results
                                                        :success)])))
                      abort?))
                  (fn ([] false) ([x y] #_(println x y) (or x y)))
                  :|| tuple futs-vecs)))))
    (pg/send-msg
     [recipient]
     (str "Aerobio job status: " exp " phase-1  " eid)
     (str "Finished " (if repk "replicates" "merged") " for " eid ))))

#_(let [a (atom [])]
  [(coll/reducem
    (fn [x y]
      (swap! a conj [x y])
      (if (= 0 x) true false))
    (fn ([] false) ([x y] (println x y) (or x y)))
    :|| (range 5) [:one :two :three :four :five])
   @a])

;;;(ns-unmap 'aerobio.htseq.common 'get-comparison-files)
(defmulti
  ^{:doc
    "Each experiment type has its own type and structure of output
files to compare (DGE rnaseq, map/fitness for tnseq, et. al.) and this
multimethod will dispatch accordingly to specific versions to aquire
and structure the groups.

Compute the set of comparison pairs for tnseq fitness
calculation. These pairs end up being pairs of corresponding map files
of strain-condition-rep derived from the comparison pairs in the
compfile. In the case of replicates, each pair must have the same
rep-id. EID is the experiment id, and comp-filename is the name of the
csv comparison file holding comparison records (pairs), default is
ComparisonSheet.csv
"
    :arglists '([exptype & args])}
  get-comparison-files
  (fn[exptype & args] exptype))

(defmulti
  get-xcomparison-files
  (fn[exptype & args] exptype))


;;;(ns-unmap 'aerobio.htseq.common 'run-phase-2)
(defmulti
  ^{:doc "Each experiment type has its own phase 2 driver, and this
 multimethod will dispatch accordingly to the specific versions."
    :arglists '([exptype & args])}
  run-phase-2
  (fn[exptype & args] exptype))


;;;(ns-unmap 'aerobio.htseq.common 'run-comparison)
(defmulti
  ^{:doc "Each experiment type has its own comparison driver, and this
 multimethod will dispatch accordingly to the specific versions."
    :arglists '([exptype & args])}
  run-comparison
  (fn[exptype & args] exptype))


;;;(ns-unmap 'aerobio.htseq.common 'run-comparison)
(defmulti
  ^{:doc "Each experiment type can have its own aggregation driver, and this
 multimethod will dispatch accordingly to the specific versions."
    :arglists '([exptype & args])}
  run-aggregation
  (fn[exptype & args] exptype))


(defn launch-action
  "Main job launch point. Takes an `eid', the `recipient' (user) a
  function `get-tool' for resolving service tools in a flow graph
  `template'. Resolves what type of action is requested and dispatches
  the request relative to a new driver thread generated by future+."
  [eid recipient get-toolinfo template & {:keys [action rep compfile status]}]
  (binding [*ns* (find-ns 'aerobio.server)]
    (prn "LAUNCH: "
         [eid recipient template action rep compfile])
    (let [exp (get-exp-info eid :exp)]
      (cond
        (#{"phase-0" "phase-0b" "phase-0c" "phase-0d"
           "phase-1" "bt2-phase-1" "bt1-phase-1"
           "star-phase-1" "star-paired-phase-1"
           "phase-2" "phase-2b"
           "phase-2-rnaseq" "phase-2-termseq"
           "phase-2-5NTap" "phase-2-5PTap"} action)
        (let [phase action]
          (cond
            (#{"phase-0" "phase-0b" "phase-0c" "phase-0d"} phase)
            (pg/future+
             (run-phase-0 eid recipient get-toolinfo template status))

            (#{"phase-1" "bt2-phase-1" "bt1-phase-1"
               "star-phase-1" "star-paired-phase-1"} phase)
            (pg/future+
             (run-phase-1 eid recipient get-toolinfo template status :repk rep))

            (#{"phase-2" "phase-2b"} phase)
            (pg/future+
             (run-phase-2 exp eid recipient get-toolinfo template status))

            ;; TermSeq !!
            (#{"phase-2-rnaseq" "phase-2-term-seq"
               "phase-2-5NTap"  "phase-2-5PTap"} phase)
            (run-phase-2 exp eid recipient get-toolinfo template phase)))

        (#{"compare" "xcompare"} action)
        (pg/future+
         (run-comparison
          exp eid recipient compfile get-toolinfo template status))

        (#{"aggregate"} action)
        (pg/future+
         (run-aggregation
          exp eid recipient compfile get-toolinfo template status))

        :else (str "LAUNCHER: unknown action request " action)))))



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
