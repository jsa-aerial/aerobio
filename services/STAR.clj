{
 :name "STAR",
 :path "STAR",

 :argcard {"--outSAMunmapped"              1 ; include unmapped reads
           "--runThreadN"                  1 ; number of threads
           "--outFileNamePrefix"           1 ; prefix for summary files
           "--outFilterScoreMinOverLread"  1 ; too short fix
           "--outFilterMatchNminOverLread" 1 ; too short fix
           "--outFilterMatchNmin"          1 ; too short fix
           "--outFilterMismatchNmax"       1 ; too short fix
           "--readFilesCommand"            1 ; zcat, others
           }

 :description  "splice aware utility for aligning reads to reference",
}
