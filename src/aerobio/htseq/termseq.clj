;;--------------------------------------------------------------------------;;
;;                                                                          ;;
;;               A E R O B I O . H T S E Q . T E R M S E Q                  ;;
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

(ns aerobio.htseq.termseq
  [:require
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
   [aerobio.pgmgraph :as pg]
   [aerobio.htseq.common :as cmn]
   [aerobio.htseq.rnaseq :as rsq]]
  )


(defn get-comparison-files-
  "Compute the set of comparison bams and the corresponding output csv
  for the count table matrix. Comparison sets are based on the
  ComparisonSheet.csv for the experiment of eid (the experiment
  id). If rep? is true, returns the comparison sets for replicates of
  comparison pairs, else returns comparison sets for combined bams."
  ([eid opt]
   (get-comparison-files- eid "ComparisonSheet.csv" opt))
  ([eid comp-filename opt]
   (let [{:keys [rep? runtype]} opt
         bpath (if rep? [:rep :bams] [:bams])
         fpath (if rep? [:rep :fcnts] [:fcnts])
         bams (apply cmn/get-exp-info eid bpath)
         fcnts (apply cmn/get-exp-info eid fpath)
         runxref (cmn/get-exp-info eid :run-xref)
         pre (some (fn[[_ [t pre]]] (when (= t runtype) pre)) runxref)
         compvec (->> comp-filename
                      (fs/join (pams/get-params :nextseq-base) eid)
                      slurp csv/read-csv rest
                      (mapv #(mapv (fn[c] (let [[s cond] (str/split #"-" c)]
                                           (str s "-" pre cond))) %)))
         bamsvec (mapv (fn[v]
                         (mapcat #(-> (fs/join bams (str % "*.bam"))
                                      fs/glob sort)
                                 v))
                       compvec)
         otcsvs (mapv (fn[v]
                        (fs/join fcnts (str (cljstr/join "-" v) ".csv")))
                      compvec)]
     (mapv #(vector %1 %2) bamsvec otcsvs))))

(defmethod cmn/get-comparison-files :termseq
  [_ & args]
  (apply get-comparison-files- args))


(defn split-filter-fastqs
  [eid]
  (cmn/split-filter-fastqs eid identity))


(defmethod cmn/get-phase-1-args :termseq
  [_ & args]
  (apply cmn/get-phase-1-args :rnaseq args))


(defn run-termseq-comparison
  [eid recipient comparison-file get-toolinfo template rtype]
  (let [_ (rsq/get-phase-2-dirs eid nil)
        _ (rsq/get-phase-2-dirs eid :rep)
        ftype "CDS" ; <-- BAD 'magic number'
        strain (first (cmn/get-exp-info eid :strains))
        refnm ((cmn/get-exp-info eid :ncbi-sample-xref) strain)
        refgtf (fs/join (cmn/get-exp-info eid :refs) (str refnm ".gtf"))
        repcfg (assoc-in template
                         [:nodes :ph2 :args]
                         [eid comparison-file
                          {:rep? true :runtype rtype}
                          ftype refgtf recipient])
        repjob (future (cmn/flow-program repcfg get-toolinfo :run true))
        cfg (assoc-in template
                      [:nodes :ph2 :args]
                      [eid comparison-file
                       {:rep? false :runtype rtype}
                       ftype refgtf recipient])
        cfgjob (future (cmn/flow-program cfg get-toolinfo :run true))]
    #_(clojure.pprint/pprint repflow)
    [repjob cfgjob]))

(defmethod cmn/run-comparison :termseq
  [_ eid recipient compfile get-toolinfo template phase]
  (let [rtype (->> phase (str/split #"-") last keyword)]
    (run-termseq-comparison
     eid recipient compfile get-toolinfo template rtype)))


(defmethod cmn/run-phase-2 :termseq
  [_ eid recipient get-toolinfo template phase]
  (cmn/run-comparison :termseq
   eid recipient "ComparisonSheet.csv" get-toolinfo template phase))

