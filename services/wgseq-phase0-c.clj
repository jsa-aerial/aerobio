
{:name "wgseq-phase0-c",
 :path "",

 :graph {:setexp {:type "func"
                  :name "set-exp"
                  :args ["#1"]}     ; eid
         :split-filter {:type "func"
                        :name "split-filter-fastqs"}
         :mail {:type "func"
                :name "mailit"
                :args ["#1"         ; eid
                       "#2"         ; recipient
                       "Aerobio job status: rnaseq phase-0"
                       "Finished"]} ; subject, body intro

         :edges {:setexp [:split-filter]
                 :split-filter [:mail]}}

 :description "WGseq with just set exp db value and then filters fastqs."
 }
