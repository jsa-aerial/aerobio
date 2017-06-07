
{:name "wgseq-phase0-b",
 :path "",

 :graph {:strtscratch {:type "func"
                       :name "start-scratch-space"
                       :args ["#1"]}
         :setexp {:type "func"
                  :name "set-exp"}
         :split-filter {:type "func"
                        :name "split-filter-fastqs"}
         :mail {:type "func"
                :name "mailit"
                :args ["#2"         ; recipient
                       "Aerobio job status: rnaseq phase-0"
                       "Finished"]} ; subject, body intro

         :edges {:strtscratch [:setexp]
                 :setexp [:split-filter]
                 :split-filter [:mail]}}

 :description "WGseq w/o bcl2fastq.  #1 arg is EID and is passed through the pipe. Creates scratch space for fastq file processing, copies fastqs to canonical dir in scratch area, sets the experiments db value and lastly filters fastqs."
 }
