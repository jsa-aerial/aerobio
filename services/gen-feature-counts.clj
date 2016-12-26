{
 :name "gen-feature-counts",
 :path "",

 :graph {:dir  {:type "func"
                :name "directory-files"
                :args ["#1", "#2"]} ; dir regex
         :fcnt {:type "func"
                :name "featureCounts"
                :args ["#3", "#4", "#5"]} ; feature-type outdir gtf

         :edges {:dir [:fcnt]}}

 :description "Produce 'feature' counts for each file in the directory (#1 arg) matching bam/sam type (#2 arg). Requires a gtf (#5 arg) matching the bam/sam reference gbk, feature-type in gtf (#3 arg 'gene', 'CDS', et.al.), and the output directory for corresponding count files (#5 arg)"
 }
