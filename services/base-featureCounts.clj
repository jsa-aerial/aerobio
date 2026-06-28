{
 :name "base-featureCounts",
 :path "",

 :graph {:fcnt {:type "func"
                :name "run-featureCounts"
                :args ["#1" "#2"]} ; options, bam
         :prn {:type "func" :name "prn"}
         :edges {:fcnt [:prn]}}

 :description "Produce 'feature' counts for bam(s) (#2 arg) using options (#1 arg) Completion msg sent to user (#3 arg)."
 }
