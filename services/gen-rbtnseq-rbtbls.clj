
{:name "gen-rbtnseq-rbtbls"
 :path ""

 :graph {:genrbtbls {:type "func"
                     :name "gen-rbtbls"
                     :args ["#6" ; EID
                            "#2" ; Chunk Size (bams)
                            "#3" ; Max n in n->1 processing
                            "#4" ; Delta, for position interval
                            "#5" ; Min reads for group inclusion
                            ]}
         :endmsg {:type "func"
                  :name "mailit"
                  :args ["#6"  ; EID
                         "#1"  ; user to send to
                         "Gen-RBTnseq-RBTbls " ; subject/job
                         "Finished:"    ; Intro
                         ]}
         :edges {:bc2fq [:mkprojs]
                 :mkprojs [:endmsg]}}

 :description "Job for generating the RB position tables for RB"}
