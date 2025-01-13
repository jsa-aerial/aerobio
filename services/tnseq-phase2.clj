
{
 :name "tnseq-phase2",
 :path  "",

 :graph {:fit  {:type "func"
                :name "tnseq-fitness"
                :args ["#1" "#2"]}   ; eid, compfile
         :mail1 {:type "func"
                 :name "mailp2"
                 :args ["#1"         ; eid
                        "#3"         ; recipient
                        "Aerobio job status: tnseq phase-2a fitness"
                        "Finished"]} ; subject, body intro
         :tnag  {:type "func"
                 :name "tnseq-aggregate"
                 :args ["#1" "#2"]}  ; eid, compfile
         :mail2 {:type "func"
                 :name "mailp2"
                 :args ["#1"         ; eid
                        "#3"         ; recipient
                        "Aerobio job status: tnseq phase-2b aggregation"
                        "Finished"]} ; subject, body intro

         :edges {:fit    [:mail1]
                 :mail1  [:tnag]
                 :tnag   [:mail2]}}

 ;; instructional data used in /help
 :description "Produce position level fitness summaries for each pair in the comparison set determined by eid (#1 arg) and the given comparison file for eid (#2 arg). Requires a gtf (#3 arg) matching the strain involved."
 }
