
{
 :name "align-fcount"
 :path ""

 :graph {:getargs
         {:type "func"
          :name "get-bulkalncnt-args"
          :args ["#1" ; id ref map file name
                 "#2" ; n fq set chunk size
                 "#3" ; user
                 "#4" ; Directory of subdirs of fastqs
                 ]}

         :align-and-count
         {:type "func"
          :name "align-and-count"}

         :aggr {:type "func"
                :name "aggregate"}

         :endmsg {:type "func"
                  :name "mailp2"
                  :args ["align-fcount"
                         "#3"         ; user
                         "Aerobio job status: align-fcount"
                         "Finished"]} ; subject, body intro
         :edges {:getargs [:align-and-count]
                 :align-and-count [:aggr]
                 :aggr [:endmsg]}}

 :description "Job to takae a dir of dirs of fastqs and align and fcount them"
}
