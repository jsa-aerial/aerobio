
{:name "tnseq-phase0-c",
 :path "",

 :graph {:bcstats {:type "func"
               :name "collect-barcode-stats"
               :args ["#1"]}        ; eid
         :wrtstats {:type "func"
                    :name "write-bcmaps"}
         :setexp {:type "func"
                  :name "set-exp"}
         :split-filter {:type "func"
                        :name "split-filter-fastqs"}
         :coll {:type "func"
                :name "collapser"}
         :mail {:type "func"
                :name "mailit"
                :args ["#1"         ; eid
                       "#2"         ; recipient
                       "Aerobio job status: tnseq phase-0c"
                       "Finished"]} ; subject, body intro

         :edges {:bcstats  [:wrtstats]
                 :wrtstats [:setexp]
                 :setexp [:split-filter]
                 :split-filter [:coll]
                 :coll [:mail]}}

 :description "Tn-Seq w/o bcl2fastq and start-scratch-space - collect barcode stats through filter/splitter. Collects barcode and NT stats, then writes that to canonical area, sets the experiments db value, splits and filters fastqs by replicates and lastly collapses those fqs."
 }
