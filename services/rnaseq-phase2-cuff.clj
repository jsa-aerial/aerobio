

{
 :name "rnaseq-phase2-cuff",
 :path  "",

 :graph {:cfmd {:type "func"
                :name "cuffmerge-cuffdiff"
                :args ["#1" "#2" "#3" "#4" "#5" "#6" "#7" "#8"]
                }
         :Rcb {:type "func"
               :name "Rcummerbund"
               :args ["#9" "#4" "#10"]
               }
         :prn {:type "func"
               :name "prn"}
         :edges {:cfmd [:Rcb] :Rcb [:prn]}}

 ;; instructional data used in /help
 :description "RNASeq flow from asembly files through cummerbund. Process assemblies through to obtain differential analysis."
 }
