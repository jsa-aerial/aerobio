
{:name "tradis-phase0-b",
 :path "",

 :graph {:strtscratch {:type "func"
                       :name "start-scratch-space"
                       :args ["#1"]} ; EID}
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

 :description "TRADis-Seq without bc2fq. Assumes fastqs pregenerated. Creates scratch space for fastq file processing, copies fastqs to canonical dir in scratch area, sets the experiments db value, generates experiment sample gDNA, then writes that to canonical area fqs, lastly collapses those fqs to fastas."
 }
