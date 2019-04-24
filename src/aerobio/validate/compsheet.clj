(ns aerobio.validate.compsheet
  (:require
   [clojure.pprint :refer [pprint]]
   [clojure.spec.alpha :as s]
   [phrase.alpha :as p
    :refer [defphraser phrase phrase-first]]

   [clojure.data.csv :as csv]
   [clojure.string :as cljstr]
   [aerial.utils.string :as str]
   [aerial.fs :as fs]

   [aerobio.params :as pams]
   [aerobio.validate.common :as vc
    :refer [validate-msg make-validator bases? cols->maps
            set-exp-sheet-data get-exp-sheet-data]]
   [aerobio.htseq.common :as ac]))


;;; ComparisonSheet.csv

;;; sample names section

(defn sample-name? [x]
  (contains? (get-exp-sheet-data (x :EID) :sampnames) (x :nm)))

(defn efstg? [s] (re-matches #"(^(?![\s\S])|[0-9]+|[0-9]+\.[0-9]+)" s))

(s/def ::sampx sample-name?)
(s/def ::sampy sample-name?)
(s/def ::ef efstg?)
(s/def ::efs (s/coll-of ::ef :kind vector?))
(s/def ::comprec (s/keys :req-un [::sampx ::sampy] :opt-un [::efs]))

(p/defphraser sample-name?
  [_ problem]
  (format "`%s` is not a sample listed in Exp-SampleSheet"
          (-> problem :val :nm)))

(p/defphraser efstg?
  [_ problem]
  (format "`%s` must be empty or digits or digits.digits. Ex 122 or 202.12"
          (-> problem :val)))

(def validate-comp-rec (make-validator ::comprec :sep "\n"))


(s/def ::comprecs (s/coll-of ::comprec :kind vector?))

(def validate-comp-recs (make-validator ::comprecs :sep "\n"))


(defn validate-comp-sheet [comprecs]
  (->> comprecs
       validate-comp-recs
       (str/split #"\n")
       (filter not-empty)
       (map #(format " %s. %s" %1 %2) (iterate inc 1))
       (cljstr/join "\n")))


;;; WGseq section

(defn Isample-name? [x]
  (contains? (get-exp-sheet-data (x :EID) :Isampnames) (x :Inm)))

(defn strain? [x]
  (contains? (get-exp-sheet-data (x :EID) :strains) (x :strain)))

(s/def ::Isamp Isample-name?)
(s/def ::strain strain?)
(s/def ::wgcomprec (s/keys :req-un [::Isamp ::strain]))

(p/defphraser Isample-name?
  [_ problem]
  (format "`%s` is not a sample listed in SampleSheet"
          (-> problem :val :Inm)))

(p/defphraser strain?
  [_ problem]
  (format "`%s` is not a strain listed in Exp-SampleSheet ncxref section"
          (-> problem :val :strain)))

(def validate-wgcomp-rec (make-validator ::wgcomprec :sep "\n"))

(s/def ::wgcomprecs (s/coll-of ::wgcomprec :kind vector?))

(def validate-wgcomp-recs (make-validator ::wgcomprecs :sep "\n"))

(defn validate-wgcomp-sheet [comprecs]
  (->> comprecs
       validate-wgcomp-recs
       (str/split #"\n")
       (filter not-empty)
       (map #(format " %s. %s" %1 %2) (iterate inc 1))
       (cljstr/join "\n")))


;;; Main - validate general comparison sheet)

(defn validate-wgcomparison-sheet
  [EID rows]
  (let [cols [:sampname :refname]
        recs (cols->maps cols rows)
        recs (mapv (fn[m] {:Isamp {:EID EID :Inm (m :sampname)}
                          :strain {:EID EID :strain (m :refname)}
                          :EID EID})
                   recs)]
    (validate-wgcomp-sheet recs)))

(defn validate-rtcomparison-sheet
  [EID rows]
  (let [tnseq? (= (ac/get-exp-info EID :exp) :tnseq)
        cols [:sampx :sampy]
        efs (when tnseq?
              (mapv #(vec (drop 2 %)) rows))
        recs (cols->maps cols rows)
        recs (mapv (fn[m] {:sampx {:EID EID :nm (m :sampx)}
                          :sampy {:EID EID :nm (m :sampy)}
                          :EID EID})
                   recs)
        recs (if (not tnseq?)
               recs
               (mapv #(assoc %1 :efs %2) recs efs))]
    (validate-comp-sheet recs)))

(defn validate-comparison-sheet
  ([EID compname]
   (let [compsheet (fs/join (pams/get-params :nextseq-base)
                            EID compname)
         rows (->> compsheet slurp csv/read-csv rest)
         exp (ac/get-exp-info EID :exp)
         vstg (cond
                (= exp :wgseq) (validate-wgcomparison-sheet EID rows)
                :else (validate-rtcomparison-sheet EID rows))]
     (with-out-str
       (when (not (empty? vstg))
         (print (format "%s has errors\n" compsheet)))
       (print vstg))))
  ([EID]
   (validate-comparison-sheet EID "ComparisonSheet.csv")))


(comment

  (def EID "181013_NS500751_0092_AH57C5BGX9")
  (def EID "190218_NS500751_0120_AHNK5TBGX9")
  (fs/join "/NextSeq2/" EID "Exp-SampleSheet.csv")

  (validate-comp-sheet EID)

  (def EID "190109_NS500751_0113_AHFCW3AFXY")
  (print (validate-comparison-sheet EID))

  )
