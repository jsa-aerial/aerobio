;;--------------------------------------------------------------------------;;
;;                                                                          ;;
;;            A E R O B I O . H T S E Q . B U L K A L N C N T               ;;
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


(ns aerobio.htseq.bulkalncnt
  [:require
   [clojure.string :as str]
   [clojure.set :as set]
   
   [taoensso.timbre :as timbre
    :refer (tracef debugf infof warnf errorf)]

   [tablecloth.api :as tc]
   
   [aerial.fs :as fs]
   [aerial.utils.string :as austr]
   [aerial.utils.coll :as coll :refer [vfold reducem]]

   [aerobio.params :as pams]

   [aerobio.htseq.common  :as cmn]
   ])




(defn get-refmap [dirdir idrefmap user]
  (-> (fs/join dirdir idrefmap)
      (tc/dataset {:key-fn keyword})
      (tc/add-column
       :counter (fn[ds] (if-let [c (ds :counter)] c "featureCounts")))
      (tc/rows :as-maps)
      (->> (group-by :id)
           (mapv (fn[[id [m]]] [id  (assoc m :user user)]))
           (into {})
           (#(assoc % :dirdir dirdir)))))

(defn get-todoids [refmap]
  (let [dirdir (refmap :dirdir)
        dirs-to-process (-> refmap keys set (disj :dirdir))]
    (-> dirdir (fs/directory-files "")
        (->> (filter fs/directory?)
             (mapv fs/basename)
             (filter dirs-to-process)))))

(defn get-fqdirs [refmap]
  (let [dirdir (refmap :dirdir)
        todoids (get-todoids refmap)]
    (->> todoids
         (mapv #(do [% (fs/join dirdir %)]))
         (into {}))))

(defn get-todoents [refmap]
  (let [todoids (get-todoids refmap)
        fqdirs (get-fqdirs refmap)]
    (reduce (fn[M [id fq]]
              (assoc M id (assoc (refmap id) :fqdir fq)))
            {} fqdirs)))


(defn grp-entryfqs [fqs]
  (->> fqs
       (group-by
        (fn[f] (-> f fs/basename (str/split #"\.")
                  first (str/split #"(-|_)R(1|2)")
                  first)))))




(defn merge-args [job defopts useropts]
  (let [argcards ((aerobio.server/get-toolinfo job :bulk) :argcard)]
    (loop [args []
           dops defopts
           uops useropts]
      (cond
        (empty? dops)
        (coll/concatv args uops)
        (empty? uops)
        (coll/concatv args dops)
        :else
        (let [da (first dops)
              n (inc (argcards da))
              i (.indexOf uops da)]
          (if (neg? i)
            (recur (coll/concatv args (coll/takev n dops))
                   (coll/dropv n dops)
                   uops)
            (recur (coll/concatv args (coll/takev n (coll/dropv i uops)))
                   (coll/dropv n dops)
                   (coll/concatv (coll/takev i uops)
                                 (coll/dropv (+ i n) uops)))))))))


;;; (ns-unmap 'aerobio.htseq.bulkalncnt 'get-job-args)
(defmulti get-job-args
  "Obtain aligner specific arg vector for job"
  {:arglists '([entrymap])}
  (fn[entrymap]
    (infof "get-job-args: '%s'" entrymap)
    (entrymap :job)))


(defmethod get-job-args "bowtie2"
  [entrymap]
  (let [defopts ["-p" "16" "--very-sensitive"]
        useropts (entrymap :alnopts)
        opts (if useropts
               (merge-args "bowtie2"
                           defopts
                           (-> useropts (str/split #" ") vec))
               defopts)
        index ["-x" (entrymap :bt2idx)]
        baseargs (coll/concatv opts index)
        bamprefix (entrymap :bamprefix)]
    (when (not (fs/directory? bamprefix)) (fs/mkdirs bamprefix))
    (->> entrymap :fqmap
         (mapv (fn[[prefix fqs]]
                 (let [bam (fs/join bamprefix (str prefix ".bam"))
                       bai (str bam ".bai")
                       fqs (if (= 2 (count fqs))
                             ["-1" (first fqs) "-2" (second fqs)]
                             ["-U" (first fqs)])]
                   [baseargs fqs bam bai]))))))


(defmethod get-job-args "STAR"
  [entrymap]
  (let [defopts ["--runThreadN" "24"
                 "--readFilesCommand" "zcat"
                 "--outStd" "BAM_SortedByCoordinate"
                 "--outSAMtype" "BAM" "SortedByCoordinate"]
        useropts (entrymap :alnopts)
        opts (if useropts
               (merge-args "STAR"
                           defopts
                           (-> useropts (str/split #" ") vec))
               defopts)
        indexprefix ["--outFileNamePrefix" (entrymap :starprefix)
                     "--genomeDir" (entrymap :staridx)] 
        baseargs (coll/concatv opts indexprefix)
        bamprefix (entrymap :bamprefix)
        starprefix (entrymap :starprefix)
        ]
    (when (not (fs/directory? bamprefix)) (fs/mkdirs bamprefix))
    (when (not (fs/directory? starprefix)) (fs/mkdirs starprefix))
    (->> entrymap :fqmap
         (mapv (fn[[prefix fqs]]
                 (let [bam (fs/join bamprefix (str prefix ".bam"))
                       bai (str bam ".bai")
                       fqs (if (= 2 (count fqs))
                             ["--readFilesIn" (first fqs) (second fqs)]
                             ["--readFilesIn" (first fqs)])]
                   [baseargs fqs bam bai]))))))


(defmethod get-job-args "featureCounts"
  [entrymap]
  (let [defopts ["-t" "CDS"]
        useropts (entrymap :fcopts)
        opts (if useropts
               (merge-args "featureCounts"
                           defopts
                           (-> useropts (str/split #" ") vec))
               defopts)
        gtf ["-a" (entrymap :gtf)]
        user (entrymap :user)
        bams (entrymap :bams)
        fcntprefix (entrymap :fcntprefix)
        baseargs (coll/concatv opts gtf)]
    (when (not (fs/directory? fcntprefix)) (fs/mkdirs fcntprefix))
    (->> bams
         (mapv (fn [bam]
                 (let [bamnm (fs/basename bam)
                       csv (fs/join fcntprefix (fs/replace-type bamnm ".csv"))]
                   [(coll/concatv baseargs ["-o" csv]) bam user]
                   ))))))
















(defmethod cmn/resultset->msgset "bulk-align"
  [result-maps])

(defmethod cmn/resultset->msgset "bulk-fcount"
  [result-maps])

(defmethod cmn/resultset->msgset "bulk-align-count"
  [result-maps])







;;; for auto ns require to server
(let [ns (ns-name *ns*)]
  [ns "bac"])
