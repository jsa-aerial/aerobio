(ns aerobio.validate.all
  (:require
   [clojure.pprint :refer [pprint]]
   [clojure.spec.alpha :as s]
   [phrase.alpha :as p
    :refer [defphraser phrase phrase-first]]

   [clojure.string :as cljstr]
   [aerial.utils.string :as str]
   [aerial.fs :as fs]

   [aerobio.params :as pams]
   [aerobio.validate
    [common :as vc
     :refer [make-sheet-fspec eolOK?
             set-exp-sheet-data validate-msg make-validator]]
    [sampsheet :refer [validate-sample-sheet]]
    [expsheet :refer [validate-exp-sample-sheet]]
    [compsheet :refer [validate-comparison-sheet]]]

   [aerobio.htseq.common :as ac]))


(defn check-for-sampsheet [EID]
  (fs/file? (make-sheet-fspec EID "SampleSheet.csv")))

(defn check-for-expsheet [EID]
  (fs/file? (make-sheet-fspec EID "Exp-SampleSheet.csv")))

(defn check-for-compsheet [EID]
  (fs/file? (make-sheet-fspec EID "ComparisonSheet.csv")))

(s/def ::sampsheet check-for-sampsheet)
(s/def ::expsheet  check-for-expsheet)
(s/def ::compsheet check-for-compsheet)

(s/def ::sheets?
  (s/keys :req-un [::sampsheet ::expsheet ::compsheet]))


(p/defphraser check-for-sampsheet
  [_ problem]
  "is missing required 'SampleSheet.csv'")

(p/defphraser check-for-expsheet
  [_ problem]
  "is missing required 'Exp-SampleSheet.csv'")

(p/defphraser check-for-compsheet
  [_ problem]
  "is missing required 'ComparisonSheet.csv'")

(def validate-files-exist (make-validator ::sheets? :sep "\n"))

(defn validate-sheets-exist [EID]
  (let [vstg (->> (mapv #(vector % EID) [:sampsheet :expsheet :compsheet])
                  (into {})
                  validate-files-exist
                  (str/split #"\n")
                  (filter #(not (empty? %)))
                  (map #(format " %s. %s" %1 %2) (iterate inc 1))
                  (cljstr/join "\n"))]
    (with-out-str
      (when (not (empty? vstg))
        (print (format "Experiment '%s' - \n" EID)))
      (print vstg))))




(defn validate-exp [EID action]
  (set-exp-sheet-data EID)
  (let [vse (validate-sheets-exist EID)]
    (with-out-str
      (if (not (empty? vse))
        (print vse)
        (print (cljstr/join
                "\n\n"
                (case action
                  "phase-0"
                  (filter #(not (empty? %))
                          [(validate-sample-sheet EID)
                           (validate-exp-sample-sheet EID)])
                  "phase-1"
                  (filter #(not (empty? %))
                          [(validate-sample-sheet EID)
                           (validate-exp-sample-sheet EID)
                           #_(validate-data-sets-phase-1 EID)])
                  ("all" "phase-2" "compare" "xcompare")
                  (filter #(not (empty? %))
                          [(validate-sample-sheet EID)
                           (validate-exp-sample-sheet EID)
                           (validate-comparison-sheet EID)]) )))))))


(comment

  (aerobio.htseq.common/set-exp "181013_NS500751_0092_AH57C5BGX9")
  (aerobio.htseq.common/set-exp "190218_NS500751_0120_AHNK5TBGX9")
  (aerobio.htseq.common/set-exp "190109_NS500751_0113_AHFCW3AFXY")
  (aerobio.htseq.common/set-exp "190321_NS500751_0124_AH7JMYBGXB")
  (aerobio.htseq.common/set-exp "150708_Nutr_dropout")

  (def EID "181013_NS500751_0092_AH57C5BGX9")
  (print (validate-exp EID))
  (def EID "190218_NS500751_0120_AHNK5TBGX9")
  (print (validate-exp EID))
  (def EID "190321_NS500751_0124_AH7JMYBGXB")
  (print (validate-exp EID))

  (def EID "150708_Nutr_dropout")
  (print (validate-exp EID))

  (def EID "190109_NS500751_0113_AHFCW3AFXY")
  (print (validate-exp EID))

  )
