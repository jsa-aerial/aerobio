(ns iobio.htseq.rnaseq
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

   [aerial.bio.utils.filters :as fil]

   [iobio.params :as pams]
   [iobio.htseq.common :as cmn]
   [iobio.pgmgraph :as pg]]
  )


(def exp-info (atom {}))
;;; "/data1/NextSeq/TVOLab/AHL7L3BGXX"
;;; "/data1/NextSeq/TVOLab/AHL7L3BGXX/Docs/SampleSheet.csv"
;;; "/data1/NextSeq/TVOLab/AHL7L3BGXX/Docs/Exp-AntibioticsRTS_KZ_SD_index.csv"

(defn get-comparison-files
  "Compute the set of comparison bams and the corresponding output csv
  for the count table matrix. Comparison sets are based on the
  ComparisonSheet.csv for the experiment of eid (the experiment
  id). If rep? is true, returns the comparison sets for replicates of
  comparison pairs, else returns comparison sets for combined bams."
  ([eid rep?]
   (get-comparison-files eid "ComparisonSheet.csv" rep?))
  ([eid comp-filename rep?]
   (let [bpath (if rep? [:rep :bams] [:bams])
         fpath (if rep? [:rep :fcnts] [:fcnts])
         bams (apply cmn/get-exp-info eid bpath)
         fcnts (apply cmn/get-exp-info eid fpath)
         compvec (->> comp-filename
                      (fs/join (pams/get-params :nextseq-base) eid)
                      slurp csv/read-csv rest)
         bamsvec (mapv (fn[v]
                         (mapcat #(-> (fs/join bams (str % "*.bam"))
                                      fs/glob sort)
                                 v))
                       compvec)
         otcsvs (mapv (fn[v]
                        (fs/join fcnts (str (cljstr/join "-" v) ".csv")))
                      compvec)]
     (mapv #(vector %1 %2) bamsvec otcsvs))))


(defn run-rnaseq-phase-0
  [eid get-toolinfo template]
  (let [rnas0 template
        cfg (-> (assoc-in rnas0 [:nodes :rsqp0 :args] [eid])
                (pg/config-pgm-graph-nodes get-toolinfo nil nil)
                pg/config-pgm-graph)
        ;;_ (clojure.pprint/pprint cfg)
        futs-vec (->> cfg pg/make-flow-graph pg/run-flow-program)]
    (mapv (fn[fut] (deref fut)) futs-vec)))


(defn get-phase-1-args [eid repname & {:keys [repk]}]
  (let [fqs (cljstr/join "," (cmn/get-replicate-fqzs eid repname repk))
        refnm (cmn/replicate-name->strain-name eid repname)
        btindex (fs/join (cmn/get-exp-info eid :index) refnm)
        otbam (fs/join (cmn/get-exp-info eid repk :bams) (str repname ".bam"))
        otbai (str otbam ".bai")
        refgtf (fs/join (cmn/get-exp-info eid :refs)
                        (str refnm ".gtf"))
        cuffot (fs/join (cmn/get-exp-info eid repk :cuffs) repname)]
    (apply cmn/ensure-dirs (map fs/dirname [otbam otbai cuffot]))
    [btindex fqs otbam otbai refgtf cuffot]))

(defn all-phase-1-args [eid]
  (->> :sample-names
       (cmn/get-exp-info eid)
       (map (fn[rnm] [rnm (get-phase-1-args eid rnm)]))
       (into {})))

(defn run-rnaseq-phase-1
  [eid get-toolinfo template & {:keys [repk]}]
  (let [rnaseq-phase1-job-template template
        sample-names (cmn/get-exp-info eid :sample-names)
        sample-names (if repk
                       (mapcat #((cmn/get-exp-info eid :replicate-names) %)
                               (cmn/get-exp-info eid :sample-names))
                       sample-names)]
    (doseq [tuple (partition-all 2 sample-names)]
      (let [futs-vecs
            (mapv
             (fn[snm]
               (let [rnaseq-job (assoc-in rnaseq-phase1-job-template
                                          [:nodes :rsqp1 :args]
                                          (get-phase-1-args eid snm :repk repk))
                     cfg (-> rnaseq-job
                             (pg/config-pgm-graph-nodes get-toolinfo nil nil)
                             pg/config-pgm-graph)]
                 #_(clojure.pprint/pprint cfg)
                 (->> cfg pg/make-flow-graph pg/run-flow-program)))
             tuple)]
        (mapv (fn[futs] (mapv (fn[fut] (deref fut)) futs))
              futs-vecs)))))


(defn get-phase-2-dirs [eid nm repk]
  (let [cuffbase  (fs/join (cmn/get-exp-info eid repk :cuffs))
        charts    (fs/join (cmn/get-exp-info eid repk :charts))
        charts    (fs/join charts nm)
        asms      (fs/join (cmn/get-exp-info eid repk :asms))
        diffs     (fs/join (cmn/get-exp-info eid repk :diffs))
        bams      (fs/join (cmn/get-exp-info eid repk :bams))
        merge-dir (fs/join asms nm)]
    (cmn/ensure-dirs charts asms diffs)
    [cuffbase charts asms diffs bams merge-dir]))

(defn get-phase-2-args [eid nm & {:keys [repk]}]
  (let [[cuffbase
         charts
         asms
         diffs
         bams
         merge-dir] (get-phase-2-dirs eid nm repk)
        strain (->> nm (str/split #"-") first)
        replicates (if repk
                     ((cmn/get-exp-info eid :replicate-names) nm)
                     (->> (cmn/get-exp-info eid :sample-names)
                          (filter (fn[n]
                                    (= nm (->> n (str/split #"-") first))))))
        repstr   (cljstr/join "," replicates)
        cuffdirs (->> replicates
                      (map (fn[rnm] (fs/join cuffbase rnm)))
                      sort vec)
        bamfiles (->> replicates
                      (map (fn[rnm] (fs/join bams (str rnm ".bam"))))
                      sort vec)
        cuffdiff (fs/join diffs nm)
        refnm     ((cmn/get-exp-info eid :ncbi-sample-xref) strain)
        merge-gtf (fs/join merge-dir "merged.gtf")
        asm-txt   (fs/join asms (str nm "-assembly.txt"))
        refgtf    (fs/join (cmn/get-exp-info eid :refs) (str refnm ".gtf"))
        reffna    (fs/join (cmn/get-exp-info eid :refs) (str refnm ".fna"))
        comps (if repk
                repstr
                (->> "ComparisonSheet.csv"
                     (fs/join (pams/get-params :nextseq-base) eid)
                     slurp csv/read-csv rest
                     (map (fn[[x y]] (str x ":" y))) (cljstr/join ",")))]
    (io/with-out-writer asm-txt
        (doseq [d cuffdirs]
          (println (fs/join d "transcripts.gtf"))))
    [merge-dir asm-txt refgtf
     cuffdiff reffna repstr merge-gtf bamfiles
     charts comps]))

(defn get-phase-2-noassembly-args
  [eid nm & {:keys [repk]}]
  (let [[merge-dir asm-txt refgtf
         cuffdiff reffna repstr
         merge-gtf bamfiles
         chart-dir comps] (get-phase-2-args eid "316")]
    [cuffdiff reffna repstr
     refgtf bamfiles
     chart-dir comps]))

(defn run-rnaseq-phase-2
  [eid get-toolinfo template & {:keys [repk]}]
  (let [rnaseq-phase2-job-template template
        sampnms (cmn/get-exp-info eid :sample-names)
        strains (cmn/get-exp-info eid :strains)
        names (if repk sampnms strains)]
    (doseq [tuple (partition-all 4 names)]
      (let [futs-vecs
            (mapv
             (fn[snm]
               (let [rnaseq-job (assoc-in rnaseq-phase2-job-template
                                          [:nodes :rsqp2 :args]
                                          (get-phase-2-args eid snm :repk repk))
                     cfg (-> rnaseq-job
                             (pg/config-pgm-graph-nodes get-toolinfo nil nil)
                             pg/config-pgm-graph)]
                 #_(clojure.pprint/pprint cfg)
                 (->> cfg pg/make-flow-graph pg/run-flow-program)))
             tuple)]
        (mapv (fn[futs] (mapv (fn[fut] (deref fut)) futs))
              futs-vecs)))))


