{
 :name "make-refdata"
 :path ""

 :graph {:refdata {:type "func"
                   :name "refdata"
                   :args ["#1" ; mulitloc?, 'n'
                          "#2" ; regex for multiloc, default NC_[0-9]+
                          "#4" ; gbk comma separated namelist
                          "#5" ; gbk directory
                          "#6" ; fasta directory
                          ]}
         :endmsg {:type "func"
                  :name "mailp2"
                  :args ["make-refdata"
                         "#3"         ; user, default NONE
                         "Aerobio job status: make-refdata"
                         "Finished"]} ; subject, body intro
         :edges {:refdata [:endmsg]}}

 :description "Job to make all reference data (aligner indices, etc) for genome gbks"
}
