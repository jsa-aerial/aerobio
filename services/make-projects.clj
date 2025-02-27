

{:name "make-projects"
 :path ""
 :func (fn [eid]
         (let [expbase (pams/get-params :exp-base)
               fqpath  (pams/get-params :elembio-fqdir)
               newprefix (-> eid (str/split #"_") first)
               header ["SampleName" "Index1" "Index2" "Lane" "Project"]
               runmancsv "RunManifest.csv"
               make-manifest (fn[lines dir]
                               (let [the-csv (fs/join dir runmancsv)]
                                 [the-csv
                                  (-> lines
                                      (tc/dataset)
                                      (tc/rename-columns #(nth header %))
                                      (tc/add-column
                                       "Lane"
                                       (fn[ds]
                                         (let [cnt (-> ds (tc/group-by "Lane")
                                                       tc/row-count)
                                               lane (ds "Lane")]
                                           (mapv (fn[l] (if (= 1 cnt) nil l))
                                                 lane)))))]))
               projmap (-> expbase (fs/join eid runmancsv)
                           (tc/dataset {:header-row? false})
                           (tc/rows)
                           (->> (coll/dropv-until #(= "SampleName" (first %)))
                                (drop 1)
                                (drop-while #(re-find #"PhiX" (first %)))
                                (group-by last)))
               projfqs (->> projmap keys sort
                            (map #(fs/join expbase eid fqpath % "*.fastq.gz")))
               newdirs (->> projmap keys sort
                            (map #(fs/join expbase (str newprefix "_"  %))))
               newfqs (->> newdirs (map #(fs/join % fqpath)))
               newcsvs (map (fn[proj dir]
                              (make-manifest (projmap proj) dir))
                            (-> projmap keys sort)
                            newdirs)]
           (doseq [dpath newfqs]
             (println :mkdirs dpath)
             (fs/mkdirs dpath))
           (doseq [[pfqs nfqs]
                   (->> newfqs (interleave projfqs) (partition-all 2))]
             (println :mv pfqs :-> nfqs)
             (fs/move pfqs nfqs))
           (doseq [dir newdirs]
             (let [rpjson "RunParameters.json"
                   oldrp (fs/join expbase eid rpjson)
                   newrp (fs/join dir rpjson)]
               (println :cp rpjson :-> dir)
               (fs/copy oldrp newrp)))
           (doseq [csv newcsvs]
             (println :write-new-manifest (first csv))
             (tc/write! (second csv) (first csv)))
           [newdirs newfqs]))

 :description "Make the new projects in a split-output run"}
