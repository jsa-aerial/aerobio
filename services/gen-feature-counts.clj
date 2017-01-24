{
 :name "gen-feature-counts",
 :path "",

 :graph {:dir  {:type "func"
                :name "get-comparison-files"
                :args ["#1", "#2", "#3" ]} ; eid, comparison-file, rep?
         :fcnt {:type "func"
                :name "featureCounts"
                :args ["#3", "#4"]} ; feature-type, gtf

         :edges {:dir [:fcnt]}}

 :description "Produce 'feature' counts for each pair in the comparison set determined by eid (#1 arg), the given comparison file for eid (#2 arg) and whether replicates are used (#3 arg). Requires a gtf (#5 arg) matching the bam reference gbk and feature-type in gtf (#4 arg 'gene', 'CDS', et.al.)."
 }
