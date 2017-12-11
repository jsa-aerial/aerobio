
{
 :name "rnaseq-star-phase1",
 :path  "",

 :graph {:star {:type "tool"
                :path "STAR"
                :args ["--genomeDir" "#1" ; STAR index dir
                       "--runThreadN" "24"
                       "--readFilesCommand" "zcat"
                       "--readFilesIn" "#2" ; input fastq.gz
                       "--outStd" "BAM_SortedByCoordinate"
                       "--outSAMtype" "BAM" "SortedByCoordinate"
                       "--outFileNamePrefix" "#5"]} ; STAR prefix name
         :cl1 {:type "func"
               :name "write-bam"
               :args ["#3"]}
         :st3 {:type :tool
               :path "samtools"
               :args ["index" "-" "#4"]}
         :edges {:star [:cl1 :st3]}}

 ;; instructional data used in /help
 :description "Process sequencer fastqs using STAR splice junction aware aligner generating sorted bam, to bam indexing, to bam write - all via streaming connections.",
 }
