
{:name "wgseq-phase0",
 :path "",

 :graph {:bc2fq {:type "func"
                 :name "bcl2fastq"
                 :args ["#1"]}
         :strtscratch {:type "func"
                       :name "start-scratch-space"}
         :setexp {:type "func"
                  :name "set-exp"}
         :split-filter {:type "func"
                        :name "split-filter-fastqs"}
         :mail {:type "func"
                :name "mailit"
                :args ["#2"         ; recipient
                       "Aerobio job status: rnaseq phase-0"
                       "Finished"]} ; subject, body intro

         :edges {:bc2fq [:strtscratch]
                 :strtscratch [:setexp]
                 :setexp [:split-filter]
                 :split-filter [:mail]}}

 :description "WGseq bcl2fastq through filter of input fastq. Process sequencer bcl files to fastq for experiment data identifiied by EID (argument #1 to bc2fq). Then creates scratch space for fastq file processing, copies fastqs to canonical dir in scratch area, sets the experiments db value and lastly filters fastqs."
 }
