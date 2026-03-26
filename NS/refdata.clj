
(in-ns 'aerobio.htseq.common)

(require '[clojure.java.io :as jio])


(defn cat> [files outfile]
  (with-open [out (jio/output-stream outfile)]
    (doseq [f files]
      (jio/copy (jio/file f) out))))

(defn make-symlink [f symf]
  (when (and (not (fs/file? symf))
             (not (fs/symbolic-link? symf)))
    (pg/ln "-s" f symf)))


(defn get-norm-recs [gbfile]
  (let [v (bufiles/genbank-recs
           gbfile :feats ["CDS"] :attrs ["locus_tag" "product"])]
    (->> v (drop 1)
         (filter (fn[[_ _ attrs]]
                   (let [ltag (attrs "locus_tag" "")
                         product (cljstr/lower-case (attrs "product" ""))]
                     (or (cljstr/index-of product "transposase")
                         (cljstr/index-of product "mobile element")))))
         vec)))

(defn make-norm-gene-file [gbfiles outfile]
  (letio [gbfiles (if (coll? gbfiles) gbfiles [gbfiles])
          ot (io/open-file outfile :out)]
    (doseq [gbfile gbfiles]
      (let [ltags (->> gbfile
                       get-norm-recs
                       (mapv (fn[[_ _ attrs]]
                               (str/substring (attrs "locus_tag") 1 -1)))
                       sort)]
        (doseq [ltag ltags]
          (.write ot (str ltag "\n")))))))


(defn gbff->chr-gbks [ingbk otbase
                      & {:keys [regexp] :or {regexp #"NC_[0-9]+"}}]
  (letio [in (io/open-file ingbk :in)]
    (loop [l (.readLine in)
           LOCcnt 0]
      (if (nil? l)
        LOCcnt
        (if (and (re-find #"^LOCUS" l) (re-find regexp l))
          (let [nc (re-find regexp l)
                nc (cond (string? nc) nc
                         (vector? nc) (first nc)
                         :else
                         (throw
                          (exp-info
                           (format "Unknown RE return '%s'\n'%s'" nc l)
                           {})))
                ot (io/open-file (fs/join otbase (str nc ".gbk")) :out)]
            (println :LOC nc)
            (.write ot (str l "\n"))
            (loop [l (.readLine in)]
              (.write ot (str l "\n"))
              (if (= l "//")
                (do (. ot close) :done)
                (recur (.readLine in))))
            (recur (.readLine in) (inc LOCcnt)))
          (recur (.readLine in) LOCcnt))))))


(defn make-ref-data [nm gbkbase fnabase]
  (try
    (let [nm (fs/replace-type nm "")
          refdir (pams/get-params [:refdata :refdir])
          bt1index (fs/join refdir (pams/get-params [:refdata :bt1idx]))
          bt2index (fs/join refdir (pams/get-params [:refdata :bt2idx]))
          normsdir (fs/join refdir (pams/get-params [:refdata :normgenes]))
          gbk (fs/join gbkbase (str nm ".gbk"))
          fna (fs/join fnabase (str nm ".fna"))
          gtf (fs/join refdir (str nm ".gtf"))
          normstxt (fs/join normsdir (str nm ".txt"))
          ]
      (make-norm-gene-file gbk normstxt)
      (bufiles/gbank->fna gbk fna)
      (pg/bowtie-build fna (fs/join bt1index nm))
      (pg/bowtie2-build fna (fs/join bt2index nm))
      (bufiles/genbk2gtf gbk gtf)
      (make-symlink gbk (->> gbk fs/basename (fs/join refdir)))
      :success)
    (catch Exception e
      (errorf "make-ref-data '%s': EXCEPTION %s"
              nm (or (.getMessage e) e))
      :failed)))


(defn main-locus [gbfile]
  (let [rdr (clojure.java.io/reader gbfile)
        locus (->> rdr .readLine (str/split #"\s+") vec)]
    (.close rdr)
    (second locus)))


(defn process-multi-loc-gbk [gbk loc-re gbkbase fastabase]
  (try
    (let [refdir (pams/get-params [:refdata :refdir])
          bt1index (fs/join refdir (pams/get-params [:refdata :bt1idx]))
          bt2index (fs/join refdir (pams/get-params [:refdata :bt2idx]))
          normsdir (fs/join refdir (pams/get-params [:refdata :normgenes]))
          gbk (if (fs/dirname gbk) gbk (fs/join gbkbase gbk))
          loc-re (re-pattern loc-re)
          gbkdir (fs/join gbkbase "LocOut")
          _ (fs/mkdirs gbkdir)
          fnadir  (fs/join fastabase "LocOut")
          _ (fs/mkdirs fnadir)
          loccnt (gbff->chr-gbks gbk gbkdir :regexp loc-re)
          locgbks (-> gbkdir (fs/join "*.gbk") fs/glob sort)]
      (when (> loccnt 0)
        (doseq [gbk locgbks]
          (let [nm (-> gbk fs/basename (fs/replace-type ""))]
            (bufiles/gbank->fna gbk (fs/join fnadir (str nm ".fna")))
            (bufiles/genbk2gtf gbk (fs/join gbkdir (str nm ".gtf")))))
        (let [mainloc (main-locus gbk)
              locgtfs (-> gbkdir (fs/join "*.gtf") fs/glob)
              locfnas (-> fnadir (fs/join "*.fna") fs/glob)
              suffix (->> (inc loccnt) (range 1) (cljstr/join "-"))
              fullfna (fs/join fnadir (str mainloc "-" suffix ".fna"))
              fullgtf (fs/join refdir (str mainloc "-" suffix ".gtf"))
              fullgbk (fs/join refdir (str mainloc "-" suffix ".gbk"))
              normstxt (fs/join normsdir (str mainloc "-" suffix ".txt"))
              nm (-> fullgbk fs/basename (fs/replace-type ""))]
          (cat> locfnas fullfna)
          (cat> locgtfs fullgtf)
          (make-norm-gene-file locgbks normstxt)
          (pg/bowtie-build fullfna (fs/join bt1index nm))
          (pg/bowtie2-build fullfna (fs/join bt2index nm))
          (doseq [gbk locgbks]
            (make-symlink gbk (->> gbk fs/basename (fs/join refdir))))
          (make-symlink gbk fullgbk)
          :success)))
    (catch Exception e
      (errorf "make-ref-data '%s/%s': EXCEPTION %s"
              gbk loc-re (or (.getMessage e) e))
      :failed)))


(defmethod resultset->msgset "make-refdata"
  [result-maps]
  (let [[locs gbk gbkdir locregex] (->> result-maps first :value)
        refdir (pams/get-params [:refdata :refdir])
        msgs (for [resmap result-maps]
               (let [name (resmap :name)
                     [locs gbk gbkdir locregex] (resmap :value)
                     exit (resmap :exit)]
                 (if (= locs :multi)
                   [exit (format "Multi locus %s/%s" gbk locregex)]
                   [exit (format "%s" gbk)])))]
    [gbkdir refdir msgs]))




;;; for auto ns require to server
(let [ns (ns-name *ns*)]
  [ns "cmn"])
