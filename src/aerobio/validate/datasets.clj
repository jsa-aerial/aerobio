(ns aerobio.validate.datasets
  (:require
   [clojure.pprint :refer [pprint]]

   [clojure.spec.alpha :as s]
   [phrase.alpha :as p
    :refer [defphraser phrase phrase-first]]

   [clojure.string :as cljstr]
   [aerial.utils.string :as str]
   [aerial.fs :as fs]

   [aerobio.params :as pams]
   [aerobio.validate.common :as vc
    :refer [validate-msg make-validator bases? cols->maps]]
   [aerobio.htseq.common :as ac]))


;;; phase-0 checks. Need to ensure 'experiment' directory exists and
;;; has the path holding the bcl files

(defmulti basecall-directory
  "Get basecall directory by instrument type"
  {:arglists '([eid])}
  (fn[eid] (ac/get-instrument-make eid)))

(defmethod basecall-directory :illum
  [eid]
  "Data/Intensities/BaseCalls")

(defmethod basecall-directory :elembio
  [eid]
  "BaseCalls")


(defn expexists? [EID]
  (fs/directory?
   (fs/join (pams/get-params :exp-base) EID)))

(defn bclfiles? [EID]
  (fs/directory?
   (fs/join (pams/get-params :exp-base)
            EID
            (basecall-directory EID))))

(s/def ::expexists? expexists?)
(s/def ::bclfiles? bclfiles?)
(s/def ::phase0-chks (s/and ::expexists? ::bclfiles?))

(p/defphraser expexists?
  [_ problem]
  (format "Experiment '%s' does not exist!"
          (-> problem :val)))

(p/defphraser bclfiles?
  [_ problem]
  (format "Experiment '%s' does not have any registered bcl files!"
          (-> problem :val)))

(def validate-expexists (make-validator ::expexists?))
(def validate-bclfiles (make-validator ::bclfiles?))
(def validate-phase-0 (make-validator ::phase0-chks :sep "\n"))




;;; phase-1 checks. We need to ensure that the current Exp-SampleSheet
;;; data match the phase-0 generated data sets

(defn samp-fqs? [pair]
  (and (vector? pair) (seq (pair 1))))

(s/def ::samp-fqs? samp-fqs?)
(s/def ::phase1-chks (s/coll-of ::samp-fqs? :kind vector?))

(p/defphraser samp-fqs?
  [_ problem]
  (format "There are no associated fastq files for sample '%s'"
          (-> problem :val first)))

(def validate-phase1 (make-validator ::phase1-chks :sep "\n"))

(defn validate-phase-1 [EID]
  (let [samp-fqs-pairs (mapv vec (ac/get-all-replicate-fqzs EID))
        vstg (->> samp-fqs-pairs
                  validate-phase1
                  (str/split #"\n")
                  (filter not-empty)
                  (map #(format " %s. %s" %1 %2) (iterate inc 1))
                  (cljstr/join "\n"))]
    (with-out-str
      (when (not (empty? vstg))
        (print (format "There are errors in '%s' phase-1 datasets\n" EID)))
      (print vstg))))



;;; phase-2 checks. We need to ensure that there are bams for each
;;; group of comparisons being requested.

(defn comp-bams? [pair]
  (and (vector? pair) (seq (pair 0))))

(s/def ::comp-bams? comp-bams?)
(s/def ::phase2-chks (s/coll-of ::comp-bams? :kind vector?))

(p/defphraser comp-bams?
  [_ problem]
  (format "There are no associated bam files for comparing '%s'"
          (-> problem :val second fs/basename (fs/replace-type ""))))

(def validate-compbams (make-validator ::comp-bams?))
(def validate-phase2 (make-validator ::phase2-chks :sep "\n"))

(defn validate-phase-2
  [EID & {:keys [compfile] :or {compfile "ComparisonSheet.csv"}}]
  (let [exp (ac/get-exp-info EID :exp)
        bams-csv-pairs (ac/get-comparison-files exp EID compfile true)
        vstg (->> bams-csv-pairs
                  validate-phase2
                  (str/split #"\n")
                  (filter not-empty)
                  (map #(format " %s. %s" %1 %2) (iterate inc 1))
                  (cljstr/join "\n"))]
    (with-out-str
      (when (not (empty? vstg))
        (print (format "There are errors in '%s' phase-2 datasets\n" EID)))
      (print vstg))))


(comment

  (validate-expexists "foo")
  (validate-bclfiles "foo")
  (validate-phase0 "Ngon")
  (validate-phase0 "Ngon_cipro")

  (cmn/get-comparison-files :rnaseq "Ngon_cipro" "ComparisonSheet.csv" true)

  (validate-phase-1 "Ngon_cipro")

  (comp-bams?
   [() "/ExpOut/Ngon_cipro/Out/Rep/Fcnts/Ng-CIP5minR-Ng-NDC5minR.csv"/])

  (validate-phase2
   '[[() "/ExpOut/Ngon_cipro/Out/Rep/Fcnts/Ng-CIP5minR-Ng-NDC5minR.csv"]
     [() "/ExpOut/Ngon_cipro/Out/Rep/Fcnts/Ng-CIP10minR-Ng-NDC10minR.csv"]
     [() "/ExpOut/Ngon_cipro/Out/Rep/Fcnts/Ng-CIP15minR-Ng-NDC15minR.csv"]
     [() "/ExpOut/Ngon_cipro/Out/Rep/Fcnts/Ng-CIP5minS-Ng-NDC5minS.csv"]
     [() "/ExpOut/Ngon_cipro/Out/Rep/Fcnts/Ng-CIP10minS-Ng-NDC10minS.csv"]
     [() "/ExpOut/Ngon_cipro/Out/Rep/Fcnts/Ng-CIP15minS-Ng-NDC15minS.csv"]])
)
