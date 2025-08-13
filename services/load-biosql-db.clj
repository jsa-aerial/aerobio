{
 :name "load-biosql-db",
 :path  "",

 :graph {:dir  {:type "func"
                :name "directory-files"
                :args ["#1" "#2"]} ; dir regex
         :load {:type "func"
                :name "load-genbank"
                :args ["#3" "#4"]} ; dbname pwd
         :move {:type "func"
                :name "move-processed-file"
                :args ["#5" "#6"]} ; done-dir err-dir
         :aggr {:type "func"
                :name "aggregate"}
         :msg  {:type "func"
                :name "mailp2"
                :args ["load-database"
                       "#7"         ; recipient
                       "Aerobio job status: load-biosql-db"
                       "Finished"]} ; subject, body intro

         :edges {:dir [:load]
                 :load [:move]
                 :move [:aggr]
                 :aggr [:msg]}}

 :description "Load a set of GenBank files (typically from a RefSeq download, but could be others such as HOMD) into an existing biosql schema database. The database needs to have been created already."
 }
