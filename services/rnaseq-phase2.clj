

{
 :name "rnaseq-phase2",
 :path  "",

 :graph {:fcnt {:type "func"
                :name "gen-feature-counts"
                :args ["#1" "#2" "#3" "#4" "#5"]
                }
         :mail {:type "func"
                :name "mailit"}
         :edges {:fcnt [:mail]}}

 ;; instructional data used in /help
 :description "RNASeq flow from asembly files through cummerbund. Process assemblies through to obtain differential analysis."
 }
