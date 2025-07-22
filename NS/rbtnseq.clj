;;--------------------------------------------------------------------------;;
;;                                                                          ;;
;;             A E R O B I O . H T S E Q . R B T N S E Q                    ;;
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

(ns aerobio.htseq.rbtnseq
  [:require
   [clojure.core.reducers :as r]
   [clojure.string :as str]
   [clojure.set :as set]

   [taoensso.timbre :as timbre
    :refer (infof warnf errorf)]

   [tech.v3.dataset :as ds]
   [tech.v3.datatype :as dtype]
   [tech.v3.datatype.functional :as df]
   [tech.v3.dataset.reductions :as dsr]
   [tablecloth.api :as tc]
   [tablecloth.column.api :as tcc]

   [aerial.fs :as fs]
   [aerial.utils.string :as astr]
   [aerial.utils.coll :as coll]
   [aerial.utils.io :refer [letio] :as io]
   [aerial.utils.math :as m]
   [aerial.utils.math.infoth :as it]
   [aerial.bio.utils.files :as bufiles]

   [cljam.io.sam :as sam]

   [aerobio.params :as pams]
   [aerobio.tcutils :as tcu]
   [aerobio.htseq.common :as cmn]
   [aerobio.pgmgraph :as pg]])




(defn bamalns->ds [bam & {:keys [grpby]}]
  (let [nm (-> bam fs/basename (fs/replace-type ""))
        alnds (with-open [r (sam/reader bam)]
                (->> (sam/read-alignments r)
                  (r/fold
                   100
                   (fn
                     ([] [])
                     ([V v]
                      (reduce (fn [V rec]
                                (conj V rec))
                              V v)))
                   (fn[V m]
                     (if (not= (bit-and (:flag m) 2r100) 0)
                       ;; unmapped read, drop it
                       V
                       (let [qname (:qname m)
                             bc (-> qname (str/split #":")
                                  (nth 7) (str/split #" ") first)]
                         (conj V {:rname (:rname m)
                                  :flag (:flag m)
                                  :barcode bc
                                  :seq (:seq m)
                                  :pos (:pos m)
                                  :cigar (:cigar m)})))))
                  tc/dataset))]
    (tc/set-dataset-name
     (if grpby (tc/group-by alnds grpby) alnds)
     nm)))


(defn bamsalns->datasets
  ([bams grpby]
   (pmap (fn[bam] (bamalns->ds bam :grpby grpby))
         bams))
  ([bamdir bamglob & {:keys [grpby]}]
   (let [bams (-> bamdir (fs/join bamglob) fs/glob sort)]
     (bamsalns->datasets bams grpby))))


(defn get-basegrps [grpds]
  (-> grpds :data
    (->>
      (mapv
       (fn[ds]
         (-> ds
           (->> (tcu/reduce-cols
                 [:barcode :pos :flag]
                 {:count (dsr/row-count)}))
           (tc/select-columns [:rname :barcode :flag :pos :count :cigar])
           (tc/order-by :barcode)
           (tc/select-rows #(> (% :count) 9))
           (tc/group-by :barcode) (tc/order-by :pos)))))))

(defn get-basegrping
  [grp rcnt-interval & {:keys [ungroup?] :or {ungroup? false}}]
  (let [[s e] rcnt-interval
        finish (if ungroup? tc/ungroup identity)]
    (-> grp
      (tc/without-grouping->
       (tc/order-by #(-> % :data tc/row-count))
       (tc/select-rows #(-> % :data tc/row-count ((fn[c](< s c e))))))
      finish)))


(defn rowidx [ds]
  (let [col (-> ds :count)
        [_ [idx _]] (reduce (fn[[i P] cnt]
                              (if (> cnt (second P))
                                [(inc i) [i cnt]]
                                [(inc i) P]))
                            [0 [0 0]] col)]
    idx))

(defn coalesce [basegrping delta]
  (->> basegrping :data
    (coll/vfold
     (fn[ds]
       (if (= 1 (tc/row-count ds))
         ds
         (let [idx (rowidx ds)
               r (tc/select-rows ds idx)
               p (-> r :pos first)
               [s e] [(max 0 (- p delta)) (+ p delta)]
               rds (tc/select-rows ds #(<= s (% :pos) e))]
           (if (not= (tc/row-count rds) (tc/row-count ds))
             (tc/dataset)
             (tc/dataset
              {:rname (-> rds :rname first)
               :barcode (-> rds :barcode first)
               :flag (-> rds :flag first)
               :pos (-> rds :pos first)
               :count (->> rds :count (reduce +))
               :cigar (-> rds :cigar first)}))))))
    (apply tc/concat-copying)))


(defn coalesce-by-bcs [ds]
  (let [grpds (-> ds (tc/order-by [:pos :count] [:asc :desc])
                (tc/group-by [:rname :pos])
                (tc/without-grouping->
                 (tc/group-by #(-> % :data (tc/row-count)))))
        onebcds (-> grpds :data first :data (->> (apply tc/concat-copying)))
        othersds (->> grpds :data rest
                   (coll/vfold
                    (fn[gds]
                      (let [ds (-> gds :data first)
                            bcs (-> ds :barcode vec)
                            pbc (first bcs)
                            bcidx-pairs (->> bcs
                                          (interleave (range))
                                          (partition-all 2))
                            pass (keep (fn[[idx bc]]
                                         (when (<= (it/hamming pbc bc) 2)
                                           idx))
                                       bcidx-pairs)
                            good-ds (tc/select-rows ds pass)
                            sumcnt (reduce + (good-ds :count))]
                        (-> good-ds
                          (tc/select-rows 0)
                          (tc/add-column :count sumcnt))))))]
     (-> (apply tc/concat-copying onebcds othersds)
       (tc/order-by [:rname :pos]))))


(defn gen-rbtnseq-xreftbls [eid chksz maxn delta minrds]
  (when (not (cmn/get-exp eid)) (cmn/set-exp eid))
  (let [bamdir (cmn/get-exp-info eid :rep :bams)
        bams (-> bamdir (fs/join "*.bam") fs/glob sort)
        tbldir (fs/join (fs/dirname bamdir) "RBtbls")]
    ;; To keep parallelization from thrashing, we need to chunk this
    ;; stuff.  At the point of last experimenting, a chksz of 5 seems
    ;; to work well on a typical 64 core AMD machine.
    (loop [bams bams
           chunks []]
      (if (empty? bams)
        (->> chunks (apply concat) vec)
        (let [curbams (take chksz bams)
              alndatasets (bamsalns->datasets curbams :rname)
              chnk
              (reduce (fn[R alngrpds]
                        (let [nm (tc/dataset-name alngrpds)
                              ds (->> alngrpds
                                   get-basegrps
                                   (filter #(> (tc/row-count %) 0))
                                   (mapv
                                    (fn[grpds]
                                      (-> grpds
                                        (get-basegrping [0 maxn])
                                        (coalesce delta)
                                        (tc/select-rows
                                         #(> (% :count) minrds)))))
                                   (keep
                                    (fn[ds]
                                      (when (> (tc/row-count ds) 0) ds)))
                                   (apply tc/concat-copying)
                                   coalesce-by-bcs)]
                          (-> ds (tc/set-dataset-name nm)
                            (tcu/write-dataset tbldir))
                          (conj R [nm (->> ds :count (reduce +))])))
                      [] alndatasets)]
          (infof ">>> gen-rbtnseq-xreftbls curchunk %s" chnk)
          (recur (drop chksz bams)
                 (conj chunks chnk)))))))




(let [ns (ns-name *ns*)]
  [ns "rbtnseq"])
