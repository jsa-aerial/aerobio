
{:name "tradis-phase0",
 :path "",

 :graph {:bc2fq {:type "func"
                 :name "bc2fq"
                 :args ["#1"]}      ; eid
         :strtscratch {:type "func"
                       :name "start-scratch-space"}
         :setexp {:type "func"
                  :name "set-exp"}
         :gen-sampfqs {:type "func"
                        :name "gen-sampfqs"}
         :coll {:type "func"
                :name "collapser"}
         :mail {:type "func"
                :name "mailit"
                :args ["#1"         ; eid
                       "#2"         ; recipient
                       "Aerobio job status: tradis phase-0"
                       "Finished"]} ; subject, body intro

         :edges {:bc2fq [:strtscratch]
                 :strtscratch [:setexp]
                 :setexp [:gen-sampfqs]
                 :gen-sampfqs [:coll]
                 :coll [:mail]}}

 :description "TRADis-Seq bc2fq through gDNA generation. Process sequencer bcl files to fastq for experiment data identifiied by EID (argument #1 to bc2fq). Then creates scratch space for fastq file processing, copies fastqs to canonical dir in scratch area, sets the experiments db value, generates experiment sample gDNA, then writes that to canonical area fqs, lastly collapses those fqs."
 }
