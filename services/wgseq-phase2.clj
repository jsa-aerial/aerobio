
{
 :name "wgseq-phase2",
 :path  "",

 :graph {:dir  {:type "func"
                :name "get-comparison-files"
                :args ["#1", "#2" "#3"]} ; eid, comparison-file, :NA here
         :take {:type "func"
                :name "strm-take"
                :args [10]}
         :bseq {:type "func"
                :name "breseq-runs"}
         :aggr {:type "func"
                :name "aggregate"}
         :mail {:type "func"
                :name "mailp2"
                :args ["#4"        ; recipient
                       "Aerobio job status: wgseq phase-2"
                       "Finished"]}      ; subject, body intro
         :edges {:dir  [:take]
                 :take [:bseq]
                 :bseq [:aggr]
                 :aggr [:mail]}}

 ;; instructional data used in /help
 :description ""
 }
