

{:name "get-bulkalnfct-args"
 :path ""
 :repeat true
 :generator true
 :func
 (let [workmanifests (volatile! :nyi)
       curworkargs (volatile! :na)]
   (fn[idrefmap n user dirdir & _]
     (when (= @workmanifests :nyi)
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
             manifests
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
                                           :starprefix (fs/join
                                                        dirdir "Out"
                                                        (fs/basename fqdir)
                                                        "STAR")
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
                                           :gtf (fs/join
                                                 refdir (str refnm ".gtf")))
                           fctargs (bac/get-job-args entrymap)
                           fctjob (counter-jobs (-> :job entrymap keyword))]
                       {:ent todoent
                        :get-toolinfo get-toolinfo
                        :aln (mapv #(assoc-in alnjob [:nodes :root :args] %)
                                   alnargs)
                        :fct (mapv #(assoc-in fctjob [:nodes :root :args] %)
                                   fctargs)}))))]
         (vswap! workmanifests (fn[_] manifests))))

     (if (= @curworkargs :na)
       (let [wms @workmanifests]
         (if (seq wms)
           (let [wm (first wms)
                 ent (wm :ent)
                 aln (wm :aln)
                 fct (wm :fct)]
             (vswap! workmanifests
                     (fn[_] (rest wms)))
             (if (and (seq (drop n aln)) (seq (drop n fct)))
               (vswap! curworkargs
                       (fn[_] {:ent ent :aln (drop n aln) :fct (drop n fct)}))
               (vswap! curworkargs (fn[_] :na)))
             [ent (wm :get-toolinfo) (take n aln) (take n fct)])
           (pg/done)))
       (let [curwm @curworkargs
             ent (curwm :ent)
             aln (curwm :aln)
             fct (curwm :fct)]
         (if (and (seq (drop n aln)) (seq (drop n fct)))
           (vswap! curworkargs
                   (fn[_] {:ent ent :aln (drop n aln) :fct (drop n fct)}))
           (vswap! curworkargs (fn[_] :na)))
         [ent (curwm :get-toolinfo) (take n aln) (take n fct)]))))

 :description "Streaming bulk align and fcount args"
 }
