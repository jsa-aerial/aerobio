
{:name "tnseq-phase0-b",
 :path "",

 :graph {:strtscratch {:type "func"
                       :name "start-scratch-space"
                       :args ["#1"]} ; EID
         :bcstats {:type "func"
                   :name "collect-barcode-stats"}
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
                :args ["#2"         ; recipient
                       "Aerobio job status: tnseq phase-0b"
                       "Finished"]} ; subject, body intro

         :edges {:strtscratch [:bcstats]
                 :bcstats  [:wrtstats]
                 :wrtstats [:setexp]
                 :setexp [:split-filter]
                 :split-filter [:coll]
                 :coll [:mail]}}

 :description "Tn-Seq w/o bcl2fastq - start-scratch-space through barcode stats and filter/splitter. Using (prebuilt) fastq files creates scratch space for fastq file processing, copies fastqs to canonical dir in scratch area, collects barcode and NT stats, then writes that to canonical area, sets the experiments db value, splits and filters fastqs by replicates and lastly collapses those fqs."
 }
