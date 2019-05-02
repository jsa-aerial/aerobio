(ns aerobio.validate.expsheet
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
    :refer [make-sheet-fspec eolOK?
            validate-msg make-validator bases? cols->maps
            update-vdb get-vdb get-exp-sheet-data]]
   [aerobio.htseq.common :as ac]))


;;; Exp-SampleSheet.csv

;;; EOL check
;;;
(defn expsamp-eolok [EID]
  (eolOK? (make-sheet-fspec EID "Exp-SampleSheet.csv")))

(s/def ::expsamp-eol expsamp-eolok)

(s/def ::eolsok? (s/keys :req-un [::expsamp-eol]))

(p/defphraser expsamp-eolok
  [_ problem]
  "'Exp-SampleSheet.csv' must use Linux or Win/Dos End Of Line (EOL) markers")

(def validate-eols (make-validator ::eolsok? :sep "\n"))

(defn validate-eols-ok [EID]
  (let [expsheet (make-sheet-fspec EID "Exp-SampleSheet.csv")
        vstg (->> {:expsamp-eol EID}
                  validate-eols
                  (str/split #"\n")
                  (filter #(not (empty? %)))
                  (map #(format " %s. %s" %1 %2) (iterate inc 1))
                  (cljstr/join "\n"))]
    (with-out-str
      (when (not (empty? vstg))
        (print (format "%s has errors\n" expsheet)))
      (print vstg))))




;;; Content checks

;;; 'NC' xref section

(defn gbk? [x] (contains? (get-exp-sheet-data (x :EID) :gbks) (x :ncid)))
(defn gtf? [x] (contains? (get-exp-sheet-data (x :EID) :gtfs) (x :ncid)))
(defn normgenes? [x] (contains? (get-exp-sheet-data (x :EID) :norms) (x :ncid)))
(defn index? [x] (contains? (get-exp-sheet-data (x :EID) :indices) (x :ncid)))
(defn bt1index? [x]
  (contains? (get-exp-sheet-data (x :EID) :bt1indices) (x :ncid)))

(s/def ::gbk? gbk?)
(s/def ::gtf? gtf?)
(s/def ::index? index?)
(s/def ::bt1index? bt1index?)
(s/def ::normgenes? normgenes?)

(s/def ::ncid string?)
(s/def ::expnm string?)

(s/def ::nc-xref-rec
  (s/merge (s/keys :req-un [::ncid ::expnm])
           ::gbk? ::gtf? ::index? ::bt1index? ::normgenes?))
(s/def ::nc-xref-recs (s/coll-of ::nc-xref-rec))


(p/defphraser gbk?
  [_ problem]
  (format "reference `%s` does not have a registered gbk"
          (-> problem :val :ncid)))
(p/defphraser gtf?
  [_ problem]
  (format "reference `%s` does not have a registered gtf"
          (-> problem :val :ncid)))
(p/defphraser index?
  [_ problem]
  (format "reference `%s` does not have a Bowtie-2 index"
          (-> problem :val :ncid)))
(p/defphraser bt1index?
  [_ problem]
  (format "reference `%s` does not have a Bowtie-1 index"
          (-> problem :val :ncid)))
(p/defphraser normgenes?
  [_ problem]
  (format "reference `%s` does not have normalization genes registered"
          (-> problem :val :ncid)))

(def validate-nc-xref-rec (make-validator ::nc-xref-rec))
(def validate-nc-xref-recs (make-validator ::nc-xref-recs))


;;; strain-condtion-repid section

(defn make-scr-rec [EID x]
  (let [[s c r o] (str/split #"-" x)
        dups (get-exp-sheet-data EID :sampdups)
        rec {:EID EID, :s s :c c :r r :v x :dupcnt (dups x 0)}]
    (if o (assoc rec :o o) rec)))

(defn strain? [x]
  (contains? (get-exp-sheet-data (x :EID) :strains) (x :s)))

(defn nodup? [x] (= 0 (x :dupcnt)))
(defn cond-ok? [x] (re-matches #"[A-Za-z0-9]+" (x :c)))
(defn repid-ok? [x] (re-matches #"[A-Z]|[a-z]|[0-9]" (x :r)))
(defn len3? [x] (= (count (dissoc x :EID :v :dupcnt)) 3))

(s/def ::len3? len3?)
(s/def ::strain? strain?)
(s/def ::nodup? nodup?)
(s/def ::cond-ok? cond-ok?)
(s/def ::repid-ok? repid-ok?)
(s/def ::strain-cond-rep?
  (s/merge ::len3? ::strain? ::cond-ok? ::repid-ok? ::nodup?))


(p/defphraser len3?
  [_ problem]
  (format "`%s` must have exactly two '-' characters!"
          (->> problem :val :v)))

(p/defphraser strain?
  [_ problem]
  (let [fieldval (->> problem :val :v)
        strain (-> problem :val :s)]
    (format
     "strain `%s` in `%s` is not in the NC xref section of Exp-SampleSheet"
     strain fieldval)))

(p/defphraser cond-ok?
  [_ problem]
  (let [fieldval (->> problem :val :v)
        condition (-> problem :val :c)]
    (format
     "condition `%s` in `%s` contains a punctuation or other illegal character"
     condition fieldval)))

(p/defphraser repid-ok?
  [_ problem]
  (let [fieldval (->> problem :val :v)
        repid (-> problem :val :r)]
    (format
"rep id `%s` in `%s` must be a single upper case or lower case letter or digit"
     repid fieldval)))

(p/defphraser nodup?
  [_ problem]
  (let [fieldval (-> problem :val :v)
        cnt (-> problem :val :dupcnt)]
    (format "entry `%s` is duplicated %s times - must be unique"
            fieldval cnt)))

(def validate-sample-field
  (make-validator ::strain-cond-rep? :sep " and\n     " :suffix "\n"))


(defn validate-sample-fields [sampfields]
  (->> sampfields
       (map validate-sample-field)
       (filter not-empty)
       (map #(format "  %s. %s" %1 %2) (iterate inc 1))
       (cljstr/join "\n")))


;;; strain-condition-rep Illumina and sample BC xref section

(defn xref-illumbc? [x]
  (contains? (get-exp-sheet-data (x :EID) :bcsets) (x :illumbc)))
(defn bc-rep-xref-scr? [x]
  (empty? (validate-sample-field x)))

(s/def ::strain-cond-repid bc-rep-xref-scr?)
(s/def ::illumbc bases?)
(s/def ::xref-illumbc? xref-illumbc?)
(s/def ::sampbc  bases?)
(s/def ::bc-replicate-xref-rec
  (s/merge
   (s/keys :req-un [::strain-cond-repid ::illumbc ::sampbc])
   ::xref-illumbc?))

(s/def ::bc-replicate-xref-recs (s/coll-of ::bc-replicate-xref-rec))


(p/defphraser bc-rep-xref-scr?
  [_ problem]
  (validate-sample-field (-> problem :val)))

(p/defphraser bases?
  {:via [::sampbc]}
  [_ problem]
  (format "Sample BC `%s` must contain only 'A', 'T', 'G', 'C' characters"
          (-> problem :val)))

(p/defphraser bases?
  {:via [::illumbc]}
  [_ problem]
  (format "Illumina BC `%s` must contain only 'A', 'T', 'G', 'C' characters"
          (-> problem :val)))

(p/defphraser xref-illumbc?
  [_ problem]
  (format "Illumina BC `%s` is not listed as an index in SampleSheet.csv"
          (-> problem :val :illumbc)))


(def validate-sample-field
  (make-validator ::strain-cond-rep? :sep "\n"))

(def validate-bc-rep-xref
  (make-validator ::bc-replicate-xref-rec :sep "\n" :suffix "\n"))


(defn validate-bc-rep-xrefs [xref-recs]
  (->> xref-recs
       (map validate-bc-rep-xref)
       (filter not-empty)
       (map #(format "%s. %s" %1 %2) (iterate inc 1))
       (cljstr/join "\n")))


;;; Exp-SampleSheet section

(s/def ::ncxref ::nc-xref-recs)
(s/def ::repxref ::bc-replicate-xref-recs)
(s/def ::exp-samp-sheet (s/keys :req-un [::ncxref ::repxref]))


(def validate-exp-samp-sheet
  (make-validator ::exp-samp-sheet :sep "\n"))

(defn validate-exp-sheet [exp-samp-sheet]
  (->> exp-samp-sheet
       validate-exp-samp-sheet
       (str/split #"\n")
       (filter #(not (empty? %)))
       (map #(format " %s. %s" %1 %2) (iterate inc 1))
       (cljstr/join "\n")))


(defn validate-exp-sample-sheet [EID]
  (let [veol (validate-eols-ok EID)]
    (if (not-empty veol)
      veol
      (let [exp-sampsheet (make-sheet-fspec EID "Exp-SampleSheet.csv")
            recs (ac/get-exp-sample-info exp-sampsheet)
            exp-rows (recs :exp-rec)
            exp-cols [:type :name :comment]
            exp-rec (first (cols->maps exp-cols exp-rows))

            ncbi-xref-rows (recs :ncbi-xref)
            ncbi-cols [:num :ncid :expnm]
            ncbi-recs (mapv #(assoc (dissoc % :num) :EID EID)
                            (cols->maps ncbi-cols ncbi-xref-rows))

            bc-xref-rows (->> recs :bc-xref
                              (reduce (fn[R [n scr ibc sbc]]
                                        (if (empty? scr)
                                          R
                                          (assoc R scr
                                                 [(Integer. n) scr ibc sbc])))
                                      {})
                              vals (sort-by first) (mapv #(-> % rest vec)))
            bc-cols [:strain-cond-repid :illumbc :sampbc]
            bc-recs (mapv #(assoc (dissoc % :num)
                                  :EID EID
                                  :strain-cond-repid
                                  (make-scr-rec EID (% :strain-cond-repid)))
                          (cols->maps bc-cols bc-xref-rows))

            exp-sheet {:ncxref ncbi-recs :repxref bc-recs}
            vstg (if (#{"rnaseq" "tnseq" "termseq"} (exp-rec :type))
                   (validate-exp-sheet exp-sheet)
                   (validate-nc-xref-recs ncbi-recs))]
        #_(do (pprint exp-rec)
              (pprint ncbi-recs)
              (pprint bc-recs))
        (with-out-str
          (when (not (empty? vstg))
            (print (format "%s has errors\n" exp-sampsheet)))
          (print vstg))))))




(comment

  (def EID "181013_NS500751_0092_AH57C5BGX9")
  (def EID "190218_NS500751_0120_AHNK5TBGX9")

  (fs/join "/NextSeq2/" EID "Exp-SampleSheet.csv")


  (pprint (s/valid? ::ncid "NC_003028"))
  (s/explain-data ::nc-xref-rec {:ncid "NC_003028", :expnm "T4P"})
  (print (validate-nc-xref-rec {:EID EID :ncid "NC_003028", :expnm "T4P"}))

  (s/explain-data ::strain-cond-rep?
                  (make-scr-rec EID "T4P-NDC10MIN-A"))
  (pprint
   (s/explain-data ::strain-cond-rep?
                   (make-scr-rec EID "T4-NDC10MIN-A")))


  (validate-sample-field (make-scr-rec EID "T4P-NDC10MIN-Ax"))

  (print
   (validate-sample-fields
    [(make-scr-rec EID "T4P-NDC05MIN-A")
     (make-scr-rec EID "T4P-NDC10MIN-A")
     (make-scr-rec EID "T4-NDC10MIN-Ax")
     (make-scr-rec EID "T4P-NDC10MIN-%")
     (make-scr-rec EID "T4P-NDC10MI.N-1")
     (make-scr-rec EID "T4P-NDC-10MIN-1")]))

  (print
   (validate-nc-xref-recs
    [{:EID EID :ncid "NC_003028", :expnm "T4P"}
     {:EID EID :ncid "NC_003028", :expnm "T4P"}
     {:EID EID :ncid "NC_003028x", :expnm "T4P"}]))

  (pprint
   (s/explain-data ::bc-replicate-xref-rec
                   {:EID EID
                    :strain-cond-repid (make-scr-rec EID "T4P-006PEN120MIN-CC"),
                    :illumbc "TACTTAGCx",
                    :sampbc "TGGTCCT"}))

  (print
   (validate-bc-rep-xref
    {:EID EID
     :strain-cond-repid (make-scr-rec EID "T4P-006PEN120.MIN-CC"),
     :illumbc "TACTTAGCA",
     :sampbc "TGGTCCTx"}))

  #_{:strain-cond-repid "T4P-006PEN120MIN-C",
     :illumbc "TACTTAGC",
     :sampbc "TGGTCCT"}

  (print
   (validate-bc-rep-xrefs
    [{:EID EID
      :strain-cond-repid (make-scr-rec EID "T4P-006PEN120MIN-C"),
      :illumbc "TACTTAGC",
      :sampbc "TGGTCCT"}
     {:EID EID
      :strain-cond-repid (make-scr-rec EID "T4P-006PEN120.MIN-CC"),
      :illumbc "TACTTAGC",
      :sampbc "TGGTCCTx"}
     {:EID EID
      :strain-cond-repid (make-scr-rec EID "T4-006PEN120MIN-C"),
      :illumbc "TACTTAG",
      :sampbc "TGGTCCT"}]))

  (print
   (validate-exp-sheet
    {:ncxref [{:EID EID :ncid "NC_003028", :expnm "T4P"}
              {:EID EID :ncid "NC_003028", :expnm "T4P"}
              {:EID EID :ncid "NC_003028x", :expnm "T4P"}]
     :repxref [{:EID EID
                :strain-cond-repid (make-scr-rec EID "T4P-006PEN120MIN-C"),
                :illumbc "TACTTAGC",
                :sampbc "TGGTCCT"}
               {:EID EID
                :strain-cond-repid (make-scr-rec EID "T4P-006PEN120.MIN-CC"),
                :illumbc "TACTTAGC",
                :sampbc "TGGTCCTx"}
               {:EID EID
                :strain-cond-repid (make-scr-rec EID "T4-006PEN120MIN-C"),
                :illumbc "TACTTAG",
                :sampbc "TGGTCCT"}]}))
  )
