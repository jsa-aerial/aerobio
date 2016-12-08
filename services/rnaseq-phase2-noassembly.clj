
{
 :name "rnaseq-phase2-noassembly",
 :path  "",

 :graph {:cmp {:type "tool"
               :name "cuffcompare"
               :args ["-r" "#4" "-Q" "-s" "#2" "-o" "#?" "#4"]}
         :cdf {:type "tool"
               :name "cuffdiff"
               :args ["-p" "16" "-o" "#1" "-b" "#2" "-L" "#3" "-u" "#4" "#5"]
               }
         :Rcb {:type "func"
               :name "Rcummerbund"
               :args ["#6" "#1" "#7"]
               }
         :prn {:type "func"
               :name "prn"}
         :edges {:cdf [:Rcb] :Rcb [:prn]}}

 ;; instructional data used in /help
 :description "RNASeq flow from asembly files through cummerbund. Process assemblies through to obtain differential analysis."
 }
