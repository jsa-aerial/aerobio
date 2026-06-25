{
 :name "base-featureCounts",
 :path "",

 :graph {:fcnt {:type "func"
                :name "run-featureCounts"
                :args ["#1" "#2"]} ; options, bam
         :aggr {:type "func"
                :name "aggregate"}
         :endmsg {:type "func"
                  :name "mailp2"
                  :args ["bulk-featureCounts"
                         "#3"            ; user
                         "Aerobio job status: bulk-featureCounts"
                         "Finished"]} ; subject, body intro
         :edges {:fcnt [:aggr] :aggr [:endmsg]}}

 :description "Produce 'feature' counts for bam(s) (#2 arg) using options (#1 arg) Completion msg sent to user (#3 arg)."
 }
