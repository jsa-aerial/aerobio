(ns aerobio.validate.common
  (:require
   [clojure.pprint :refer [pprint]]
   [clojure.spec.alpha :as s]
   [spec-provider.provider :as pp]
   [phrase.alpha :as p
    :refer [defphraser phrase phrase-first]]

   [clojure.string :as cljstr]
   [aerial.utils.string :as str]
   [aerial.fs :as fs]

   [aerobio.htseq.common :as ac]))


;;; ::id 'foo "xxc"
;;;
(defn validate-msg
  [spec x & {:keys [sep prefix suffix val?]
             :or {sep " and " prefix "" suffix "" val? false}}]
  (let [msgbody (->> (s/explain-data spec  x) ::s/problems
                     (mapv #(phrase {} %))
                     (cljstr/join sep))
        msgval (if val? (format ">> %s <<: " x) "")]
    (str msgval prefix msgbody suffix)))


(defn make-validator [spec & validate-msg-kvs]
  (fn [x]
    (if (not (s/valid? spec x))
      (apply validate-msg spec x validate-msg-kvs)
      "")))


(defphraser :default
  [_ problem]
  problem)

(p/defphraser coll?
  [_ problem]
  (let [value (problem :val)]
    (format "`%s` is not a collection" value)))


;;; The only reason we need this is because spec incorrectly uses
;;; unqualified naming for straight core.map? predicate.
;;;
(defn vmap? [x] (map? x))

(p/defphraser vmap?
  [_ problem]
  (format "`%s` must be a map" (problem :val)))


(def bases-regex  #"[ACGT]+")
(defn bases? [x] (and (string? x) (re-matches bases-regex x)))




(defn cols->maps [cols recs]
  (->> recs (mapv #(->> %1 (mapv vector cols) (into {})))))


(defn get-ref-names [fspecs]
  (-> fspecs
      (->> (map fs/basename))
      (->> (map #(fs/replace-type % "")))
      set))

(defn get-known-refs [EID]
  (let [refdir (ac/get-exp-info EID :refs)
        indexdir (ac/get-exp-info EID :index)
        bt1indexdir (ac/get-exp-info EID :bt1index)
        gbks (get-ref-names (fs/directory-files refdir ".gbk"))
        gtfs (get-ref-names (fs/directory-files refdir ".gtf"))
        indices (->> (fs/directory-files indexdir ".rev.1.bt2")
                     get-ref-names
                     (map #(->> % (str/split #"\.") first)) set)
        bt1indices (->> (fs/directory-files bt1indexdir ".rev.1.ebwt")
                        get-ref-names
                        (map #(->> % (str/split #"\.") first)) set)
        norms (get-ref-names
               (fs/directory-files (fs/join refdir "NormGenes") ".txt"))]
    [gbks gtfs indices bt1indices norms]))

(def exp-sheet-data (atom {}))

(defn set-exp-sheet-data [EID]
  (let [exinfo (ac/get-exp EID)
        bcsets (->> exinfo :barcode-maps
                    (mapv (fn[[k v]] [k (-> v keys set)]))
                    (into {}))
        sampbcs (->> bcsets vals (apply concat) set)
        NCs (->> exinfo :exp-sample-info :ncbi-xref
                 (mapv second) (into #{}))
        strains (exinfo :strains)
        [gbks gtfs indices bt1indices norms] (get-known-refs EID)]
    #_(pprint [(take 3 bcsets) NCs strains])
    (swap! exp-sheet-data
           assoc
           EID {:bcsets bcsets :sampbcs sampbcs :ncs NCs :strains strains
                :gbks gbks :gtfs gtfs
                :indices indices :bt1indices bt1indices
                :norms norms})))

(defn get-exp-sheet-data [EID k]
  (get-in @exp-sheet-data [EID k]))


(comment

  (let [recs (ac/get-exp-sample-info
              (fs/join "/NextSeq2/" EID "Exp-SampleSheet.csv"))
        ncbi-xref-rows (recs :ncbi-xref)
        ncbi-cols [:num :ncid :expnm]
        ncbi-recs (mapv #(dissoc % :num) (cols->maps ncbi-cols ncbi-xref-rows))
        bc-xref-rows (recs :bc-xref)
        bc-cols [:num :strain-cond-repid :illumbc :sampbc]
        bc-recs (mapv #(dissoc % :num) (cols->maps bc-cols bc-xref-rows))]
    (pprint ncbi-recs)
    (pprint (take 3 bc-recs))))
