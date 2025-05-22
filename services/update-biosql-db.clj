{
 :name "update-biosql-db",
 :path  "",

 :graph {:dir  {:type "func"
                :name "directory-files"
                :args ["#1" "#2"]} ; dir regex
         :update {:type "func"
                  :name "update-genbank"
                  :args ["#3" "#4"]} ; dbname pwd
         :move {:type "func"
                :name "move-processed-file"
                :args ["#5" "#6"]} ; done-dir err-dir

         :edges {:dir [:update] :update [:move]}}

 :description "Update a biosql db with the data from  a set of GenBank files (typically from a RefSeq download, but could be others such as HOMD). The database needs to already exist and is presumed to be populated with older entries corresponding to those in the genbank files."
 }
