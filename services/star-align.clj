
{
 :name "star-align",
 :path  "",

 :graph {:star {:type "tool"
                :path "STAR"
                :args ["#1" "#2"]}
         :cl1 {:type "func"
               :name "write-bam"
               :args ["#3"]}
         :st3 {:type :tool
               :path "samtools"
               :args ["index" "-" "#4"]}
         :edges {:star [:cl1 :st3]}}

 ;; instructional data used in /help
 :description "",
 }
