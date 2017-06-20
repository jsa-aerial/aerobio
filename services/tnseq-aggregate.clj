
{
 :name "tnseq-aggregate",
 :path "",
 :func (fn [eid compfile data]
         (when (and (pg/eoi? data) (pg/done? data))
           (infof "Tn-Seq aggregate starting... %s" data)
           (let [script (fs/join (fs/pwd) "Scripts" "aggregate.pl")
                 grps (cmn/get-comparison-files :tnseq eid compfile true)
                 refdir (cmn/get-exp-info eid :refs)]
             (mapcat
              (fn[grp]
                (when (seq grp)
                  (let [pairs (butlast grp)
                        allins (mapv second pairs)
                        allcsv (last grp)
                        strain (-> pairs ffirst fs/basename
                                   (str/split #"-") first)
                        refnm  ((cmn/get-exp-info eid :ncbi-sample-xref) strain)
                        gtf (fs/join refdir (str refnm ".gbk"))
                        norm   (fs/join refdir "NormGenes" (str refnm ".txt"))]
                    (coll/vfold
                     (fn[oiv]
                       (let [ret (apply
                                  pg/perl script
                                  (concat ["-m" norm "-w" "1" "-x" "10"
                                           "-l" "50" "-b" "0" "-c" gtf "-o"]
                                          oiv [{:verbose true :throw false}]))
                             exit-code @(ret :exit-code)
                             exit (if (= 0 exit-code) :success exit-code)
                             err (-> (ret :stderr) (str/split #"\n") last)]
                         {:name "tnseq-aggregate"
                          :value oiv
                          :exit exit
                          :err err}))
                     (conj pairs (coll/concatv [allcsv] allins))))))
              grps))))

 :description "Function wrapping calc_fitness.py taking cmp-quads [t1 t2 csv ef]."
}
