

{:name "refdata"
 :path ""
 :func (fn [multiloc regex namelist gbkdir fastadir]
         (infof "RefData Multi? %s, RegEx %s, GBKS %s, GBKDir %s, FastaDir %s"
                multiloc regex namelist gbkdir fastadir)
         (let [multiloc (-> multiloc (str/split #",")
                            (->> (mapv #(fs/replace-type % ""))))
               regex (-> regex (str/split #",") (->> (mapv re-pattern)))
               gbk-ftype-pairs (-> namelist (str/split #",")
                                   (->> (mapv #(->> (str/split % #"\.")
                                                    ((fn [e]
                                                       (if (> (count e) 1)
                                                         e
                                                         [(e 0) "gbk"])))))))
               refdir (pams/get-params [:refdata :refdir])
               jobname "make-refdata"]
           (println ">>> " multiloc regex gbk-ftype-pairs)
           (mapv
            (fn [gbk-ft]
              (let [gbkname (first gbk-ft)
                    i (.indexOf multiloc gbkname)
                    gbk (str/join "." gbk-ft)]
                (if (= i -1)
                  (let [res (cmn/make-ref-data gbk gbkdir fastadir)]
                    (infof "RefData single locus GBK %s [%s]" gbk res)
                    {:name jobname
                     :value [:single gbk gbkdir]
                     :exit res
                     :err :NA})
                  (let [locregex (regex i)
                        res (cmn/process-multi-loc-gbk
                             gbk locregex gbkdir fastadir)]
                    (infof "RefData multi locus GBK %s, regex %s [%s]"
                           gbk locregex res)
                    {:name jobname
                     :value [:multi gbk gbkdir locregex]
                     :exit res
                     :err :NA}))))
            gbk-ftype-pairs)))

 :description "Driver for making full ref sets for genomes in GBK/GBFF format"
}
