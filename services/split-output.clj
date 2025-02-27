
{:name "split-output"
 :path ""

 :graph {:bc2fq {:type "func"
                 :name "bc2fq"
                 :args ["#2"   ; EID
                        "#3"   ; expdir
                        "#4"   ; data dir
                        "--group-fastq" "--num-threads" "32"]}
         :mkprojs {:type "func"
                   :name "make-projects"}
         :endmsg {:type "func"
                  :name "mailit"
                  :args ["#2"  ; EID
                         "#1"  ; user to send to
                         "Split-Output" ; subject/job
                         "Finished:"    ; Intro
                         ]}
         :edges {:bc2fq [:mkprojs]
                 :mkprojs [:endmsg]}}
 :description "Job for splitting multi experiment sequencer runs"}
