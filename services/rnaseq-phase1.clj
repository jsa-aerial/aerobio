
{
 :name "rnaseq-phase1",
 :path  "",

 :graph {:bt1 {:type "tool"
              :path "bowtie2"
              :args ["-p" "16" "--very-sensitive" "-x" "#1" "#2"]
              }
         :st1 {:type :tool
               :path "samtools"
               :args ["view" "-b"]}
         :st2 {:type :tool
               :path "samtools"
               :args ["sort" "--threads" "8"]}
         :st3 {:type :tool
               :path "samtools"
               :args ["index" "-" "#4"]}
         :cl1 {:type "func"
               :name "write-bam"
               :args ["#3"]}
         :edges {:bt1 [:st1] :st1 [:st2] :st2 [:cl1 :st3]}}

 ;; instructional data used in /help
 :description "Process sequencer fastqs from bowtie alignment, to bam conversion, to bam sort, to bam indexing, to bam write - all via streaming connections.",
 }
