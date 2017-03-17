;;--------------------------------------------------------------------------;;
;;                                                                          ;;
;;               A E R O B I O . H T S E Q . R N A S E Q                    ;;
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

(ns aerobio.htseq.rnaseq
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

   [aerobio.params :as pams]
   [aerobio.htseq.common :as cmn]
   [aerobio.pgmgraph :as pg]]
  )


(def exp-info (atom {}))
;;; "/data1/NextSeq/TVOLab/AHL7L3BGXX"
;;; "/data1/NextSeq/TVOLab/AHL7L3BGXX/Docs/SampleSheet.csv"
;;; "/data1/NextSeq/TVOLab/AHL7L3BGXX/Docs/Exp-AntibioticsRTS_KZ_SD_index.csv"

(defn get-comparison-files-
  "Compute the set of comparison bams and the corresponding output csv
  for the count table matrix. Comparison sets are based on the
  ComparisonSheet.csv for the experiment of eid (the experiment
  id). If rep? is true, returns the comparison sets for replicates of
  comparison pairs, else returns comparison sets for combined bams."
  ([eid rep?]
   (get-comparison-files- eid "ComparisonSheet.csv" rep?))
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

(defmethod cmn/get-comparison-files :rnaseq
  [_ & args]
  (apply get-comparison-files- args))


(defn split-filter-fastqs
  [eid]
  (cmn/split-filter-fastqs eid identity))

(defn run-rnaseq-phase-0
  [eid recipient get-toolinfo template]
  (let [rnas0 template
        cfg (-> (assoc-in rnas0 [:nodes :rsqp0 :args] [eid recipient])
                (pg/config-pgm-graph-nodes get-toolinfo nil nil)
                pg/config-pgm-graph)
        ;;_ (clojure.pprint/pprint cfg)
        futs-vec (->> cfg pg/make-flow-graph pg/run-flow-program)]
    (mapv (fn[fut] (deref fut)) futs-vec)))


;;; Get primary phase 1 arguments. These are the bowtie index, the
;;; fastq set, output bam and bai file names
(defmethod cmn/get-phase-1-args :rnaseq
  [_ eid repname & {:keys [repk]}]
  (let [fqs (cljstr/join "," (cmn/get-replicate-fqzs eid repname repk))
        refnm (cmn/replicate-name->strain-name eid repname)
        btindex (fs/join (cmn/get-exp-info eid :index) refnm)
        otbam (fs/join (cmn/get-exp-info eid repk :bams) (str repname ".bam"))
        otbai (str otbam ".bai")
        refgtf (fs/join (cmn/get-exp-info eid :refs)
                        (str refnm ".gtf"))
        cuffot (fs/join (cmn/get-exp-info eid repk :cuffs) repname)]
    (apply cmn/ensure-dirs (map fs/dirname [otbam otbai cuffot]))
    [btindex fqs otbam otbai]))


(defn get-phase-1-args-cuff
  "Compute phase 1 job flow arguments when using cufflinks. This is
  like std phase 1 arguments with the addition of reference gtf file
  and cuff output directory as the two final trailing arguments."
  [eid repname & {:keys [repk]}]
  (let [refnm (cmn/replicate-name->strain-name eid repname)
        [btindex fqs otbam otbai] (cmn/get-phase-1-args
                                   :rnaseq eid repname :repk repk)
        refgtf (fs/join (cmn/get-exp-info eid :refs)
                        (str refnm ".gtf"))
        cuffot (fs/join (cmn/get-exp-info eid repk :cuffs) repname)]
    (apply cmn/ensure-dirs (map fs/dirname [otbam otbai cuffot]))
    [btindex fqs otbam otbai refgtf cuffot]))


(defn get-phase-2-dirs-cuff [eid nm repk]
  (let [cuffbase  (fs/join (cmn/get-exp-info eid repk :cuffs))
        charts    (fs/join (cmn/get-exp-info eid repk :charts))
        charts    (fs/join charts nm)
        asms      (fs/join (cmn/get-exp-info eid repk :asms))
        diffs     (fs/join (cmn/get-exp-info eid repk :diffs))
        bams      (fs/join (cmn/get-exp-info eid repk :bams))
        merge-dir (fs/join asms nm)]
    (cmn/ensure-dirs charts asms diffs)
    [cuffbase charts asms diffs bams merge-dir]))

(defn get-phase-2-args-cuff [eid nm & {:keys [repk]}]
  (let [[cuffbase
         charts
         asms
         diffs
         bams
         merge-dir] (get-phase-2-dirs-cuff eid nm repk)
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
         chart-dir comps] (get-phase-2-args-cuff eid nm)]
    [cuffdiff reffna repstr
     refgtf bamfiles
     chart-dir comps]))

(defn run-rnaseq-phase-2-cuff
  [eid comparison-file get-toolinfo template & {:keys [repk]}]
  (let [rnaseq-phase2-job-template template
        sampnms (cmn/get-exp-info eid :sample-names)
        strains (cmn/get-exp-info eid :strains)
        names (if repk sampnms strains)]
    (doseq [tuple (partition-all 4 names)]
      (let [futs-vecs
            (mapv
             (fn[snm]
               (let [rnaseq-job (assoc-in
                                 rnaseq-phase2-job-template
                                 [:nodes :rsqp2 :args]
                                 (get-phase-2-args-cuff eid snm :repk repk))
                     cfg (-> rnaseq-job
                             (pg/config-pgm-graph-nodes get-toolinfo nil nil)
                             pg/config-pgm-graph)]
                 #_(clojure.pprint/pprint cfg)
                 (->> cfg pg/make-flow-graph pg/run-flow-program)))
             tuple)]
        (mapv (fn[futs] (mapv (fn[fut] (deref fut)) futs))
              futs-vecs)))))


(defn get-phase-2-dirs [eid repk]
  (let [fcnts  (fs/join (cmn/get-exp-info eid repk :fcnts))
        charts (fs/join (cmn/get-exp-info eid repk :charts))
        ;;charts    (fs/join charts nm)
        ]
    (cmn/ensure-dirs charts fcnts)
    [fcnts charts]))

(defn run-rnaseq-comparison
  "Run a condition/replicate set of comparisons based on an experiment
  designated by eid (experiement id) and the input comparison sheet
  CSV comparison-sheet"
  [eid recipient comparison-file get-toolinfo template]
  (let [_ (get-phase-2-dirs eid nil)
        _ (get-phase-2-dirs eid :rep)
        ftype "CDS" ; <-- BAD 'magic number'
        strain (first (cmn/get-exp-info eid :strains))
        refnm ((cmn/get-exp-info eid :ncbi-sample-xref) strain)
        refgtf (fs/join (cmn/get-exp-info eid :refs) (str refnm ".gtf"))
        repcfg (assoc-in template
                         [:nodes :rsqp2 :args]
                         [eid comparison-file true ftype refgtf recipient])
        repjob (future (cmn/flow-program repcfg get-toolinfo :run true))
        cfg (assoc-in template
                      [:nodes :rsqp2 :args]
                      [eid comparison-file false ftype refgtf recipient])
        cfgjob (future (cmn/flow-program cfg get-toolinfo :run true))]
    #_(clojure.pprint/pprint repflow)
    [repjob cfgjob]))

(defn run-rnaseq-phase-2
  "OBSOLETE - remove sooner than later!"
  [eid recipient get-toolinfo template]
  (run-rnaseq-comparison
   eid recipient "ComparisonSheet.csv" get-toolinfo template))


(defmethod cmn/run-comparison :rnaseq
  [_ eid recipient compfile get-toolinfo template]
  (run-rnaseq-comparison eid recipient compfile get-toolinfo template))

(defmethod cmn/run-phase-2 :rnaseq
  [_ eid recipient get-toolinfo template]
  (run-rnaseq-comparison
   eid recipient "ComparisonSheet.csv" get-toolinfo template))



(defn launch-action
  [eid recipient get-toolinfo template & {:keys [action rep compfile]}]
  (prn "LAUNCH: "
       [eid recipient template action rep compfile])
  (cond
    (= action "compare")
    (let [compfile (if compfile compfile "ComparisonSheet.csv")]
      (run-rnaseq-comparison eid recipient compfile get-toolinfo template))

    (#{"phase-0" "phase-0b" "phase-0c" "phase-1" "phase-2"} action)
    (let [phase action]
      (cond
        (#{"phase-0" "phase-0b" "phase-0c"} phase)
        (future
          (cmn/run-phase-0 eid recipient get-toolinfo template))

        (#{"phase-1"} phase)
        (future
          (cmn/run-phase-1 eid recipient get-toolinfo template :repk rep))

        (#{"phase-2"} phase)
        (run-rnaseq-phase-2 eid recipient get-toolinfo template)))

    :else (str "RNASEQ: unknown action request " action)))
