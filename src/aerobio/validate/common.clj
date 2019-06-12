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
   [aerial.hanasu.common :as hc]

   [aerobio.params :as pams]
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


;;; Check proper line EOL for sheets
;;;
(defn make-sheet-fspec [EID sheetname]
  (fs/join (pams/get-params :nextseq-base) EID sheetname))

(defn eolOK? [csv]
  (->> csv slurp (re-find #"\n")))




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
        starindexdir (ac/get-exp-info EID :starindex)
        gbks (get-ref-names (fs/directory-files refdir ".gbk"))
        gtfs (get-ref-names (fs/directory-files refdir ".gtf"))
        indices (->> (fs/directory-files indexdir ".rev.1.bt2")
                     get-ref-names
                     (map #(->> % (str/split #"\.") first)) set)
        bt1indices (->> (fs/directory-files bt1indexdir ".rev.1.ebwt")
                        get-ref-names
                        (map #(->> % (str/split #"\.") first)) set)
        starindices (->> (fs/directory-files starindexdir "")
                         (filter fs/directory?)
                         (mapv fs/basename) set)
        norms (get-ref-names
               (fs/directory-files (fs/join refdir "NormGenes") ".txt"))]
    [gbks gtfs indices bt1indices starindices norms]))




(def sheet-db (atom {}))

(defn update-vdb
  ([keypath vorf] #_(printchan [:db :update] "UPDATE-ADB " keypath vorf)
   (hc/update-db sheet-db keypath vorf))
  ([kp1 vof1 kp2 vof2 & kps-vs]
   (apply hc/update-db sheet-db kp1 vof1 kp2 vof2 kps-vs)))

(defn get-vdb
  ([] (hc/get-db sheet-db []))
  ([key-path] #_(printchan [:db :get]"GET-ADB " key-path)
   (hc/get-db sheet-db key-path)))


(defn set-exp-sheet-data [EID]
  (let [exinfo (ac/get-exp EID)
        sample-vec (->> exinfo
                        :exp-illumina-xref
                        vals (apply concat)
                        (mapv second))
        samples (->> sample-vec (into #{}))
        sampdups (->> sample-vec
                      frequencies
                      (filter (fn[[k v]] (not= 1 v)))
                      (into {}))
        sampnames (exinfo :sample-names)

        bcsets (->> exinfo :barcode-maps
                    (mapv (fn[[k v]] [k (-> v keys set)]))
                    (into {}))
        sampbcs (->> bcsets vals (apply concat) set)

        Isampnames (->> exinfo :sample-sheet (mapv first) (into #{}))
        Isampdups (->> exinfo :sample-sheet (mapv first)
                       frequencies
                       (filter (fn[[k v]] (not= 1 v)))
                       (into {}))

        NCs (->> exinfo :exp-sample-info :ncbi-xref
                 (mapv second) (into #{}))
        strains (->> exinfo :exp-sample-info :ncbi-xref (mapv last) (into #{}))
        [gbks gtfs indices bt1indices starindices norms] (get-known-refs EID)]
    #_(pprint [(take 3 bcsets) NCs strains])
    (swap! sheet-db
           assoc
           EID {:bcsets bcsets :sampbcs sampbcs :ncs NCs :strains strains
                :samples samples :sampdups sampdups :sampnames sampnames
                :Isampnames Isampnames :Isampdups Isampdups
                :gbks gbks :gtfs gtfs
                :indices indices :bt1indices bt1indices :starindices starindices
                :norms norms})))

(defn get-exp-sheet-data [EID k]
  (get-in @sheet-db [EID k]))




(comment

  (def EID "181013_NS500751_0092_AH57C5BGX9")

  (print (validate-files-exist {:sampsheet EID :expsheet EID}))
  (print (validate-sheets-exist EID))


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
