
{
 :name "tnseq-final-aggregate",
 :path  "",

 :graph {:tnag  {:type "func"
                 :name "tnseq-global-aggregate"
                 :args ["#1" "#2" (pg/done)]} ; eid, compfile, Done indicator
         :mail {:type "func"
                :name "mailp2"
                :args ["#1"         ; eid
                       "#3"         ; recipient
                       "Aerobio job status: tnseq Final Aggregation"
                       "Finished"]} ; subject, body intro
         :edges {:tnag [:mail]}}

 ;; instructional data used in /help
 :description "Produce gene level aggregate summaries with bottleneck global processing for fitness hits in csvs given  by eid (#1 arg) and the given comparison file for eid (#2 arg)."
 }
