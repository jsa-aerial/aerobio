
{:name "deseq2-rnaseq"
 :path ""
 :func (fn[eid data-map]
         (if (and (pg/eoi? data-map))
           (pg/done)
           (let [bams-csv-pair (data-map :value)
                 [bams fcnt-csv] bams-csv-pair
                 ;; Not clear it makes sense to run on anything but Rep ...
                 rep? (re-find #"Rep" (first bams))
                 dge-path (if rep? [:rep :charts] [:charts])
                 dge-dir  (apply cmn/get-exp-info eid dge-path)
                 grps (if rep?
                        (group-by #(as-> % x
                                     (fs/basename x)
                                     (str/split x #"-")
                                     (take 2 x) (str/join "-" x))
                                  bams)
                        (group-by #(as-> % x
                                     (fs/basename x)
                                     (fs/replace-type x ""))
                                  bams))
                 reps (->> grps (map (fn[[c reps]] [c (count reps)])) (into {}))
                 [c1 c2] (as-> fcnt-csv x
                           (fs/basename x) (fs/replace-type x "")
                           (str/split x #"-") (partition-all 2 x)
                           (map #(str/join "-" %) x))
                 c1c2reps (str/join "," [c1 c2 (reps c1) (reps c2)])
                 dge-csv (str/join "-" [c1 c2 "DGE-results.csv"])
                 script-dir (fs/join (fs/pwd) "Scripts")
                 script (fs/join script-dir "deseq2-rnaseq.r")
                 ret (pg/Rscript
                      "--no-save" script
                      dge-dir fcnt-csv c1c2reps script-dir
                      {:verbose true :throw false})
                 exit-code @(ret :exit-code)
                 exit (if (= 0 exit-code) :success exit-code)
                 err (-> (ret :stderr) (str/split #"\n") last)]
             {:name "deseq2-rnaseq"
              :value [(fs/dirname fcnt-csv) (fs/basename fcnt-csv)
                      dge-dir dge-csv]
              :exit exit
              :err err})))

 :description "Streaming DESeq2 for RNA-Seq DGE analysis. Takes a stream of aerobio output maps, typically from featureCounts, where the value is the bams csv pair. Generates DESeq2 DGE analysis producing a csv, and several charts"
 }
