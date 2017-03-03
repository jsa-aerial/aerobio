
{
 :name "tnseq-phase2a",
 :path  "",

 :graph {:fit {:type "func"
               :name "tnseq-fitness"
               :args ["#1" "#2"]} ; eid, compfile
         :mail {:type "func"
                :name "mailp2"
                :args ["#3"        ; recipient
                       "Aerobio job status: tnseq phase-2a Fitness"
                       "Finished"]}      ; subject, body intro
         :edges {:fit [:mail]}}

 ;; instructional data used in /help
 :description "Produce position level fitness summaries for each pair in the comparison set determined by eid (#1 arg) and the given comparison file for eid (#2 arg)."
 }
