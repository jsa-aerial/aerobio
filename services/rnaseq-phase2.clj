
{
 :name "rnaseq-phase2",
 :path  "",

 :graph {:dir  {:type "func"
                :name "get-comparison-files"
                :args ["#1", "#2", "#3" ]} ; eid, comparison-file, rep?
         :fcnt {:type "func"
                :name "featureCounts"
                :args ["#4", "#5"]} ; feature-type, gtf
         :aggr {:type "func"
                :name "aggregate"}
         :mail {:type "func"
                :name "mailp2"
                :args ["#6"        ; recipient
                       "Aerobio job status: rnaseq phase-2"
                       "Finished"]}      ; subject, body intro
         :edges {:dir [:fcnt] :fcnt [:aggr] :aggr [:mail]}}

 ;; instructional data used in /help
 :description "Produce 'feature' counts for each pair in the comparison set determined by eid (#1 arg), the given comparison file for eid (#2 arg) and whether replicates are used (#3 arg). Requires a gtf (#5 arg) matching the bam reference gbk and feature-type in gtf (#4 arg 'gene', 'CDS', et.al.)."
 }
