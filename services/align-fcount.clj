
{
 :name "align-fcount"
 :path ""
 
 :graph {:align-and-count
         {:type "func"
          :name "align-and-count"
          :args ["#1" ; id ref map file name
                 "#2" ; user
                 "#3" ; Directory of subdirs of fastqs
                 ]}
         :endmsg {:type "func"
                  :name "mailp2"
                  :args ["align-fcount"
                         "#3"         ; user
                         "Aerobio job status: align-fcount"
                         "Finished"]} ; subject, body intro
         :edges {:align-and-count [:endmsg]}}

 :description "Job to takae a dir of dirs of fastqs and align and fcount them"
}
