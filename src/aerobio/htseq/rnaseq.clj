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
         bam-regex (if rep? "-*.bam" "*.bam")
         compvec (->> comp-filename
                      (fs/join (pams/get-params :nextseq-base) eid)
                      slurp csv/read-csv rest)
         bamsvec (mapv (fn[v]
                         (mapcat #(-> (fs/join bams (str % bam-regex))
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


(defn ensure-exp-set [eid]
  (when (not (cmn/get-exp eid)) (cmn/set-exp eid)))

(defn get-xcomparison-files-
  "Compute the set of Cross Experiment comparison bams and the
  corresponding output csv for the count table matrix. Cross
  comparison sets are based on the CrossComparisonSheet.csv for the
  pseudo experiment of eid (the pseudo experiment id). If rep? is
  true, returns the comparison sets for replicates of comparison
  pairs, else returns comparison sets for combined bams."
  ([eid rep?]
   (get-xcomparison-files- eid "ComparisonSheet.csv" rep?))
  ([eid comp-filename rep?]
   (let [bpath (if rep? [:rep :bams] [:bams])
         fpath (if rep? [:rep :fcnts] [:fcnts])
         fcnts (apply cmn/get-exp-info eid fpath)
         compvec (->> comp-filename
                      (fs/join (pams/get-params :nextseq-base) eid)
                      slurp csv/read-csv rest
                      (map (fn[[c1 c2]]
                             (let [[eid1 strain cond1] (str/split #"-" c1)
                                   _ (ensure-exp-set eid1)
                                   gb1 (str strain "-" cond1 "*.bam")
                                   c1bams (apply cmn/get-exp-info eid1 bpath)
                                   [eid2 strain cond2] (str/split #"-" c2)
                                   _ (ensure-exp-set eid2)
                                   gb2 (str strain "-" cond2 "*.bam")
                                   c2bams (apply cmn/get-exp-info eid2 bpath)]
                               [(fs/join c1bams gb1) (fs/join c2bams gb2)]))))
         bamsvec (mapv (fn[v] (mapcat #(-> % fs/glob sort) v)) compvec)
         otcsvs (mapv (fn[v] (fs/join
                             fcnts
                             (str (cljstr/join
                                   "-" (mapv #(->> % fs/basename
                                                   (str/split #"\*")
                                                   first)
                                             v))
                                  ".csv")))
                      compvec)]
     (mapv #(vector %1 %2) bamsvec otcsvs))))

(defmethod cmn/get-xcomparison-files :rnaseq
  [_ & args]
  (apply get-xcomparison-files- args))




(defn split-filter-fastqs
  [eid]
  (cmn/split-filter-fastqs eid identity))

(defn run-rnaseq-phase-0
  [eid recipient get-toolinfo template]
  (let [rnas0 template
        cfg (-> (assoc-in rnas0 [:nodes :ph0 :args] [eid recipient])
                (pg/config-pgm-graph-nodes get-toolinfo nil nil)
                pg/config-pgm-graph)
        ;;_ (clojure.pprint/pprint cfg)
        futs-vec (->> cfg pg/make-flow-graph pg/run-flow-program)]
    (mapv (fn[fut] (deref fut)) futs-vec)))


(defn get-fqs [eid repname repk]
  (->> (cmn/get-replicate-fqzs eid repname repk)
       (group-by (fn[fq] (if (re-find #"R2" fq) :R2 :R1)))
       (map (fn[[k v]] [k (cljstr/join "," v)]))
       (into {})))

;;; Get primary phase 1 arguments. These are the bowtie index, the
;;; fastq set, output bam and bai file names
(defmethod cmn/get-phase-1-args :rnaseq
  [_ eid repname & {:keys [repk star]}]
  (let [{:keys [R1 R2]} (get-fqs eid repname repk)
        refnm (cmn/replicate-name->strain-name eid repname)
        btindex (fs/join (cmn/get-exp-info eid :index) refnm)
        starindex (when star
                    (fs/join (cmn/get-exp-info eid :starindex) refnm))
        starprefix (when star
                     (fs/join (cmn/get-exp-info eid repk :star) repname))
        otbam (fs/join (cmn/get-exp-info eid repk :bams) (str repname ".bam"))
        otbai (str otbam ".bai")
        refgtf (fs/join (cmn/get-exp-info eid :refs)
                        (str refnm ".gtf"))]
    (apply cmn/ensure-dirs (map fs/dirname [otbam otbai]))
    (when star (cmn/ensure-dirs (fs/dirname starprefix)))
    (if star
      (if R2
        [starindex R1 R2 otbam otbai starprefix]
        [starindex R1 otbam otbai starprefix])
      [btindex R1 otbam otbai])))


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
  [eid recipient comparison-file get-toolinfo template status-atom]
  (let [_ (get-phase-2-dirs eid nil)
        _ (get-phase-2-dirs eid :rep)
        ftype "CDS" ; <-- BAD 'magic number'
        cfg (assoc-in template
                         [:nodes :ph2 :args]
                         [eid comparison-file true ftype :NA recipient])
        futs-vec (cmn/flow-program cfg get-toolinfo :run true)]
    (cmn/job-flow-node-results futs-vec status-atom)
    (@status-atom :done)))


#_(defmethod cmn/run-xcomparison :rnaseq
  [_ eid recipient compfile get-toolinfo template]
  (run-rnaseq-comparison
   eid recipient compfile get-toolinfo template))

(defmethod cmn/run-comparison :rnaseq
  [_ eid recipient compfile get-toolinfo template status-atom]
  (run-rnaseq-comparison
   eid recipient compfile get-toolinfo template status-atom))

(defmethod cmn/run-phase-2 :rnaseq
  [_ eid recipient get-toolinfo template status-atom]
  (run-rnaseq-comparison
   eid recipient "ComparisonSheet.csv" get-toolinfo template status-atom))


