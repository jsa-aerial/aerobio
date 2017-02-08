
{:name "tnseq-phase0-c",
 :path "",

 :graph {:bcstats {:type "func"
                   :name "collect-barcode-stats"
                   :args ["#1"]}
         :wrtstats {:type "func"
                    :name "write-bcmaps"}
         :setexp {:type "func"
                  :name "set-exp"}
         :split-filter {:type "func"
                        :name "split-filter-fastqs"}
         :mail {:type "func"
                :name "mailit"
                :args ["#2"         ; recipient
                       "Aerobio job status: tnseq phase-0c"
                       "Finished"]} ; subject, body intro

         :edges {:bcstats  [:wrtstats]
                 :wrtstats [:setexp]
                 :setexp [:split-filter]
                 :split-filter [:mail]}}

 :description "Tn-Seq w/o bcl2fastq and start-scratch-space - collect barcode stats through filter/splitter. Using (prebuilt) fastq files creates scratch space for fastq file processing, copies fastqs to canonical dir in scratch area, collects barcode and NT stats, then writes that to canonical area, sets the experiments db value and lastly splits and filters fastqs by replicates."
 }
