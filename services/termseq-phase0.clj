
{:name "termseq-phase0",
 :path "",

 :graph {:bc2fq {:type "func"
                 :name "bcl2fastq"
                 :args ["#1"]}      ; eid
         :strtscratch {:type "func"
                       :name "start-scratch-space"}
         :bcstats {:type "func"
                   :name "collect-barcode-stats"}
         :wrtstats {:type "func"
                    :name "write-bcmaps"}
         :setexp {:type "func"
                  :name "set-exp"}
         :split-filter {:type "func"
                        :name "split-filter-fastqs"}
         :mail {:type "func"
                :name "mailit"
                :args ["#1"         ; eid
                       "#2"         ; recipient
                       "Aerobio job status: termseq phase-0"
                       "Finished"]} ; subject, body intro

         :edges {:bc2fq [:strtscratch]
                 :strtscratch [:bcstats]
                 :bcstats  [:wrtstats]
                 :wrtstats [:setexp]
                 :setexp [:split-filter]
                 :split-filter [:mail]}}

 :description "Term-Seq bcl2fastq through barcode stats and filter/splitter. Process sequencer bcl files to fastq for experiment data identifiied by EID (argument #1 to bc2fq). Then creates scratch space for fastq file processing, copies fastqs to canonical dir in scratch area, collects barcode and NT stats, then writes that to canonical area, sets the experiments db value and lastly splits and filters fastqs by replicates."
 }
