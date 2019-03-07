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
    :refer [validate-msg make-validator bases? cols->maps]]
   [aerobio.htseq.common :as ac]))


;;; SampleSheet.csv

(defn id-eq-nm? [m] (= (m :sid) (m :snm)))

(s/def ::sid string?)
(s/def ::snm string?)
(s/def ::index bases?)
(s/def ::index2 bases?)
(s/def ::id-eq-nm? id-eq-nm?)

;;; NOTE!!! need to use s/merge as s/and (for some idiotic reason)
;;; uses short circuit semantics!! WTF!!!
(s/def ::samp-sheet-rec
  (s/merge (s/keys :req-un [::sid ::snm ::index] :opt-un [::index2])
           ::id-eq-nm?))

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


(def validate-samp-sheet
  (make-validator
   ::samp-sheet
   :prefix    "Problems: "
   :sep " and\n          "))


(defn validate-sample-sheet [EID]
  (let [sampsheet (fs/join (pams/get-params :nextseq-base)
                           EID "SampleSheet.csv")
        recs (ac/get-sample-info sampsheet)
        cols [:sid, :snm, :index]
        recs (cols->maps cols recs)
        valid (validate-samp-sheet recs)]
    #_(pprint recs)
    (when (not (empty? valid))
      (with-out-str
        (print (format "%s has errors\n" sampsheet))
        (print valid)))))




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

