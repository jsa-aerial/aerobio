
{
 :name "tnseq-phase2b",
 :path  "",

 :graph {:tnag  {:type "func"
                 :name "tnseq-aggregate"
                 :args ["#1" "#2" (pg/done)]} ; eid, compfile, Done indicator
         :mail {:type "func"
                :name "mailp2"
                :args ["#3"        ; recipient
                       "Aerobio job status: tnseq phase-2b Aggregation"
                       "Finished"]}      ; subject, body intro
         :edges {:tnag [:mail]}}

 ;; instructional data used in /help
 :description "Produce gene level aggregate summaries for fitness hits in csvs given  by eid (#1 arg) and the given comparison file for eid (#2 arg)."
 }
