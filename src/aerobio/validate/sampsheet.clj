(ns aerobio.validate.sampsheet
  (:require
   [clojure.pprint :refer [pprint]]

   [clojure.spec.alpha :as s]
   [spec-provider.provider :as pp]
   [phrase.alpha :as p
    :refer [defphraser phrase phrase-first]]

   [clojure.string :as cljstr]
   [aerial.utils.string :as str]
   [aerial.fs :as fs]

   [aerobio.params :as pams]
   [aerobio.validate.common :as vc
    :refer [make-sheet-fspec eolOK?
            validate-msg make-validator bases? cols->maps]]
   [aerobio.htseq.common :as ac]))


;;; SampleSheet.csv

;;; EOL check
;;;
(defn samp-eolok [EID]
  (let [name (case (ac/get-instrument-make EID)
               :illum "SampleSheet.csv"
               :elembio "RunManifest.csv"
               "Unknown-instrument-make.csv")]
    (eolOK? (make-sheet-fspec EID name))))

(s/def ::samp-eol samp-eolok)

(s/def ::eolsok? (s/keys :req-un [::samp-eol]))

(p/defphraser samp-eolok
  [_ problem]
  "'SampleSheet.csv' must use Linux or Win/Dos End Of Line (EOL) markers")

(def validate-eols (make-validator ::eolsok? :sep "\n"))

(defn validate-eols-ok [EID]
  (let [sampsheet (make-sheet-fspec EID "SampleSheet.csv")
        vstg (->> {:samp-eol EID}
                  validate-eols
                  (str/split #"\n")
                  (filter #(not (empty? %)))
                  (map #(format " %s. %s" %1 %2) (iterate inc 1))
                  (cljstr/join "\n"))]
    (with-out-str
      (when (not (empty? vstg))
        (print (format "%s has errors\n" sampsheet)))
      (print vstg))))




;;; Content checks
;;;
(defn id-eq-nm? [m]
  (or (nil? (m :sid))
      (= (m :sid) (m :snm))))

(defn nodup? [x] (= 0 (x :dupcnt)))

(s/def ::sid string?)
(s/def ::snm string?)
(s/def ::index bases?)
(s/def ::index2 bases?)
(s/def ::id-eq-nm? id-eq-nm?)
(s/def ::nodup? nodup?)

;;; NOTE!!! need to use s/merge as s/and (for some idiotic reason)
;;; uses short circuit semantics!! WTF!!!
(s/def ::samp-sheet-rec
  (s/merge (s/keys :req-un [::snm ::index] :opt-un [::sid ::index2])
           ::id-eq-nm? ::nodup?))

(s/def ::samp-sheet (s/coll-of ::samp-sheet-rec))


(p/defphraser string?
  {:via [::sid]}
  [_ problem]
  (format "Sample_ID `%s` must be a string" (-> problem :val)))
(p/defphraser string?
  {:via [::snm]}
  [_ problem]
  (format "Sample_Name`%s` must be a string" (-> problem :val)))

(p/defphraser bases?
  {:via [::index]}
  [_ problem]
  (format "Index `%s` must contain only 'A', 'T', 'G', 'C' characters"
          (-> problem :val)))
(p/defphraser bases?
  {:via [::index2]}
  [_ problem]
  (format "Index2 `%s` must contain only 'A', 'T', 'G', 'C' characters"
          (-> problem :val)))

(p/defphraser id-eq-nm?
  [_ problem]
  (let [value (problem :val)
        sid (value :sid)
        snm (value :snm)]
    (format "Sample_ID `%s` must equal Sample_NAME `%s`" sid snm)))

(p/defphraser nodup?
  [_ problem]
  (let [sidnm (-> problem :val :sid)
        cnt (-> problem :val :dupcnt)]
    (format "entry `%s` is duplicated %s times - must be unique"
            sidnm cnt)))


(def validate-ssheet (make-validator ::samp-sheet :sep "\n"))

(defn validate-samp-sheet [samp-sheet]
  (->> samp-sheet
       validate-ssheet
       (str/split #"\n")
       (filter #(not (empty? %)))
       (map #(format " %s. %s" %1 %2) (iterate inc 1))
       (cljstr/join "\n")))


(defn validate-sample-sheet [EID]
  (let [veol (validate-eols-ok EID)]
    (if (not-empty veol)
      veol
      (let [sampsheet (make-sheet-fspec EID "SampleSheet.csv")
            rows (->> EID ac/get-sample-info vec)
            dups (vc/get-exp-sheet-data EID :Isampdups)
            cols (ac/get-sample-info-colkws EID)
            recs (->> rows (cols->maps cols)
                      (mapv (fn[m] (assoc m :dupcnt (dups (m :snm) 0)))))
            valid (validate-samp-sheet recs)]
        #_(pprint recs)
        (with-out-str
          (when (not (empty? valid))
            (print (format "%s has errors\n" sampsheet)))
          (print valid))))))




(comment
  (def ssr-good {:sid "abc" :snm "abc" :index "AAAACCC"})
  (def ssr-bad {:sid "abc" :snm "abc" :index "FOO"})

  (validate-msg ::samp-sheet-rec ssr-good :prefix "")
  (validate-msg ::samp-sheet-rec ssr-bad :prefix "")


  (print
   (validate-samp-sheet
    [{:sid "abc" :snm "xyz" :index "ATGC"}
     {:sid "foo" :snm "foo" :index "AAAT"}
     {:sid "foo" :snm "foo" :index "XAAAT"}
     {:sid "foo" :snm 23 :index "AAAT"}
     {:sid "one" :snm "two" :index "GCGC"}]))


  (def EID "181013_NS500751_0092_AH57C5BGX9")

  (print (validate-sample-sheet (fs/join "/NextSeq2/" EID "SampleSheet.csv")))

  (let [sampsheet (fs/join "/NextSeq2/" EID "SampleSheet.csv")
        recs (ac/get-sample-info sampsheet)
        cols [:sid, :snm, :index]
        recs (cols->maps cols recs)
        valid (validate-samp-sheet recs)]
    #_(pprint recs)
    (when (not (empty? valid))
      (print (format "%s has errors\n" sampsheet))
      (print valid))))

