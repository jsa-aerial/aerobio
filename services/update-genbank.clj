
{
 :name "update-genbank",
 :path "",
 :func (fn [dbname dbpwd gbfile]
         (if (pg/eoi? gbfile)
           (pg/done)
           (let [ret (pg/bp_load_genbank
                      "--driver" "mysql" "--host" "127.0.0.1" "--port" "3306"
                      "--dbuser" "root" "--dbpass" dbpwd
                      "--dbname" dbname
                      "--format" "genbank"
                      "--safe" "--lookup" "--remove"
                      gbfile
                      {:verbose true :throw false})
                 exit-code @(ret :exit-code)
                 exit (if (= 0 exit-code) :success exit-code)
                 err (get-in ret [:proc :err])]
             {:name "update-genbank"
              :value gbfile
              :exit exit
              :err (mapv identity err)})))

 :description "Function wrapping bp_load_seqdatabase.pl with params for genbank files. dbname is the biosql database to update with data from gbfile and dbpwd is the pw for the root user."
}
