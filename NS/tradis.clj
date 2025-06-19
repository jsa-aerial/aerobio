;;--------------------------------------------------------------------------;;
;;                                                                          ;;
;;               A E R O B I O . H T S E Q . T R A D I S                    ;;
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

(ns aerobio.htseq.tradis
  [:require
   [clojure.string :as str]
   [clojure.set :as set]

   [aerial.fs :as fs]
   [aerial.utils.string :as astr]
   [aerial.utils.coll :as coll]
   [aerial.utils.io :refer [letio] :as io]
   [aerial.utils.math :as m]
   [aerial.utils.math.infoth :as it]
   [aerial.bio.utils.files :as bufiles]

   [aerobio.params :as pams]
   [aerobio.htseq.common :as cmn]
   [aerobio.pgmgraph :as pg]])


;;; Probably not worth doing here, but bypass it/hamming to support
;;; futher optimization via a cutoff.  This looks to give about 15%
;;; improved runtime.  Might make sense to incorporate something like
;;; this into it/hamming
;;;
(defn hamming
  "Optimized string hamming with cutoff optimization"
  [^String s1 ^String s2 ^long cutoff]
  (let [sz1 (long (count s1))
        len (long (min sz1 (long (count s2))))]
    (loop [i (long 0)
           cnt (long (Math/abs (- len sz1)))]
      (if (or (> cnt cutoff) (= i len))
        cnt
        (if (= (astr/get s1 i) (astr/get s2 i))
          (recur (inc i) cnt)
          (recur (inc i) (inc cnt)))))))

(defn get-gDNA
  ([sq]
   (get-gDNA sq "CCGGGGACTTATCAGCCAACCTGT" 4 24))
  ([sq pattern within-cnt extract-cnt]
   (let [patlen (count pattern)]
     (loop [i 0
            patterns (astr/sliding-take 1 patlen sq)]
       (let [curpat (first patterns)
             found (<= (hamming curpat pattern 2) 2)
             start (+ i patlen)
             end (+ start extract-cnt)]
         (if (or found (> i within-cnt))
           [found i (subs sq start end) start end]
           (recur (inc i) (rest patterns))))))))


(defn gen-sampfqs-
  [infq-otfq-pairs gDNA-args]
  (let [[p wc ec] gDNA-args
        gdnafn (fn[sq] (get-gDNA sq p wc ec))]
    (doall
     (pmap (fn[[infq otfq]]
             (letio [rec-chunk-size 10000
                     in (io/open-file infq :in)
                     ot (io/open-file otfq :out)]
               (try
                 (loop [inrecs (bufiles/read-fqrecs in rec-chunk-size)]
                   (if (not (seq inrecs))
                     [(fs/basename infq) :success]
                     (let [otrecs (coll/vfold
                                   (fn[[hd sq aux qc]]
                                     (let [[found i sq start end] (gdnafn sq)
                                           qc (subs qc start end)]
                                       [hd sq aux qc]))
                                   inrecs)]
                       (bufiles/write-fqrecs ot otrecs)
                       (recur (bufiles/read-fqrecs in rec-chunk-size)))))
                 (catch Exception e
                   [(fs/basename infq) :fail (or (.getMessage e) e)])
                 (finally
                   (. in close)
                   (. ot close)))))
           infq-otfq-pairs))))


(defmethod cmn/gen-sampfqs :tradis
  [_ eid]
  (let [base (cmn/get-exp-info eid :base)
        exp-illumina-xref (cmn/get-exp-info eid :exp-illumina-xref)
        illumina-sample-xref (cmn/get-exp-info eid :illumina-sample-xref)
        bc-file-specs (cmn/get-bc-file-specs
                       base exp-illumina-xref illumina-sample-xref)
        ifastqs (->> (fs/directory-files
                      (cmn/get-exp-info eid :fastq) "fastq.gz")
                     (group-by #(->> % fs/basename
                                     (re-find #"_R[1-2]")
                                     (astr/drop 1)
                                     keyword)))
        sample-illumina-xref (clojure.set/map-invert illumina-sample-xref)
        sample-ifq-xref (reduce (fn[M fq]
                                  (let [samp (->> fq fs/basename
                                                  (astr/split #"\.") first
                                                  (astr/split #"_") first)]
                                    (assoc M samp fq)))
                                {} (ifastqs :R1))
        infq-otfq-pairs (->> sample-ifq-xref keys sort
                             (mapv (fn[samp]
                                     (let [ibc (sample-illumina-xref samp)]
                                       [(sample-ifq-xref samp)
                                        ;; Here, (no barcode land) there
                                        ;; is only one possible output fq
                                        (-> ibc bc-file-specs vals first)]))))
        ph1args (->> :phase-args (cmn/get-exp-info eid) :phase1)
        args (mapv (fn[k]
                     (let [v (ph1args k)]
                       (if (= k "pattern") v (parse-long v))))
                   ["pattern" "within-cnt" "extract-cnt"])]
    (cmn/ensure-sample-dirs base illumina-sample-xref)
    (gen-sampfqs- infq-otfq-pairs args)))




(defmethod cmn/get-phase-1-args :tradis
  [_ & args]
  (apply cmn/get-phase-1-args :tnseq args))

(defmethod cmn/get-comparison-files :tradis
  [_ & args]
  (apply cmn/get-comparison-files :tnseq args))

(defmethod cmn/run-comparison :tradis
  [_ & args]
  (apply cmn/run-comparison :tnseq args))

(defmethod cmn/run-phase-2 :tradis
  [_ & args]
  (apply cmn/run-phase-2 :tnseq args))

(defmethod cmn/run-aggregation :tradis
  [_ & args]
  (apply cmn/run-aggregation :tnseq args))



(let [ns (ns-name *ns*)]
  [ns "tradis"])

