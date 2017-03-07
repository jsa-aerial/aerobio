
{
 :name "tnseq-phase1",
 :path  "",

 :graph {:bt1 {:type "tool"
               :path "bowtie2"
               :args ["-p" "16" "--very-sensitive" "-x" "#1" "-U" "#2"]
               }
         :bt2 {:type "tool"
               :path "bowtie2"
               :args ["-f" "-N" "1" "--reorder"
                      "--no-unal" "--no-head"
                      "--rdg" "11,3" "--rfg" "11,3" "--gbar" "10"
                      "-p" "8"
                      "-x" "#1" "-U" "#3"]
               }
         :sort {:type "tool"
                :path "sort"
                :args ["-k4,4g"]}
         :mmap {:type "func"
                :name "make-maps"
                :args ["#4"]}
         :st1 {:type :tool
               :path "samtools"
               :args ["view" "-b"]}
         :st2 {:type :tool
               :path "samtools"
               :args ["sort" "--threads" "8"]}
         :st3 {:type :tool
               :path "samtools"
               :args ["index" "-" "#6"]}
         :cl1 {:type "func"
               :name "write-bam"
               :args ["#5"]}
         :edges {:bt2 [:sort]
                 :sort [:mmap]
                 :bt1 [:st1] :st1 [:st2] :st2 [:cl1 :st3]}}

 ;; instructional data used in /help
 :description "Process collapsed fastas from bowtie alignment, to bam conversion and map file making, to bam sort, to bam indexing, to bam write - all via streaming connections."
 }
