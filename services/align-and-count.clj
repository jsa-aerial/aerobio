
{:name "align-and-count"
 :path ""

 :func
 (fn [idrefmap user dirdir]
   (infof "AlignAndCount  RefMap '%s', User '%s', DirDir '%s'"
          idrefmap user dirdir)
   (let [refdata (pams/get-params :refdata)
         refdir (refdata :refdir)
         bt2IdxDir (fs/join refdir (refdata :bt2idx))
         starIdxDir (fs/join refdir (refdata :staridx))
         aligner-jobs (get-jobinfo "aligner-jobs")
         counter-jobs (get-jobinfo "counter-jobs")
         get-toolinfo #(get-toolinfo % "fake-eid")
         jobname "align-and-count"

         refmap (bac/get-refmap dirdir idrefmap user)
         todoents (bac/get-todoents refmap)

         jobsets
         (->> todoents
              vals
              (mapv
               (fn[todoent]
                 (let [fqdir (todoent :fqdir)
                       fqs (->> "*.fastq*" (fs/join fqdir) fs/glob sort)
                       refnm (todoent :ref)
                       entrymap (assoc todoent
                                       :job (todoent :aligner)
                                       :bt2idx (fs/join bt2IdxDir refnm)
                                       :staridx (fs/join starIdxDir refnm)
                                       :starprefix (fs/join dirdir "Out/STAR")
                                       :bamprefix (fs/join
                                                   dirdir "Out"
                                                   (fs/basename fqdir)
                                                   "Bams")
                                       :fqmap (bac/grp-entryfqs fqs))
                       alnargs (bac/get-job-args entrymap)
                       alnjob (aligner-jobs (-> :job entrymap keyword))
                       entrymap (assoc entrymap
                                       :job (todoent :counter)
                                       :bams (mapv #(->> % butlast last)
                                                   alnargs)
                                       :fcntprefix (fs/join
                                                    dirdir "Out"
                                                    (fs/basename fqdir)
                                                    "Fcnts")
                                       :gtf (fs/join refdir (str refnm ".gtf")))
                       fctargs (bac/get-job-args entrymap)
                       fctjob (counter-jobs (-> :job entrymap keyword))]
                   [todoent
                    (->> alnargs
                         (mapv #(assoc-in alnjob [:nodes :root :args] %)))
;;;(mapv #(pg/config-pgm-graph-nodes % get-toolinfo nil nil))
;;;(mapv pg/config-pgm-graph)
                    (->> fctargs
                         (mapv #(assoc-in fctjob [:nodes :root :args] %)))])))

                    (mapv (fn[[ent alnv fctv]]
                            [ent (->> fctv
                                      (interleave alnv)
                                      (partition-all 2))])))]

     (loop [jobsets jobsets
            result []]
       (if (empty? jobsets)
         result
         (let [[ent jobdefpairs] (first jobsets)
               jobgrps (partition-all 2 jobdefpairs)]
           (reduce (fn[R [jp1 jp2]]))
           )))))
}

{:name jobname
 :value [:multi gbk gbkdir locregex]
 :exit res
 :err :NA}

 :description ""
 }

