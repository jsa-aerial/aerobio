
{:name "split-output"
 :path ""

 :graph {:bc2fq {:type "func"
                 :name "bc2fq"
                 :args ["#1"   ; EID
                        "#2"   ; expdir
                        "#3"   ; data dir
                        "--group-fastq" "--num-threads" "32"]}
         :mkprojs {:type "func"
                   :name "make-projects"}
         :endmsg {:type "func"
                  :name "mailit"
                  :args ["#1"
                         "#4"
                         "Splitting output"
                         "Finished"]}
         :edges {:bc2fq [:mkprojs]
                 :mkprojs [:endmsg]}}
 :description "Job for splitting multi experiment sequencer runs"}
