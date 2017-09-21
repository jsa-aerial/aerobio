
{:name "rnaseq-phase0-d",
 :path "",

 :graph {:setexp {:type "func"
                  :name "set-exp"
                  :args ["#1"]}
         :split-filter {:type "func"
                        :name "split-filter-fastqs"}
         :mail {:type "func"
                :name "mailit"
                :args ["#2"         ; recipient
                       "Aerobio job status: rnaseq phase-0d"
                       "Finished"]} ; subject, body intro

         :edges {:setexp [:split-filter]
                 :split-filter [:mail]}}

 :description "Just set the experiments db value and perform splits and filters fastqs by replicates."
 }
