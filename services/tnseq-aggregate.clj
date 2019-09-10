
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
                        [refnm allcsv] (last grp)
                        gtf (fs/join refdir (str refnm ".gbk"))
                        norm (fs/join refdir "NormGenes" (str refnm ".txt"))
                        main ["-m" norm "-w" "1" "-x" "10"
                              "-l" "50" "-b" "0" "-c" gtf]
                        argcard ((get-toolinfo "tnseq-aggregate" eid) :argcard)
                        cmdargs ((cmn/get-exp-info eid :cmdsargs) "aggregate")
                        theargs (pg/merge-arg-sets argcard main cmdargs)
                        theargs (conj theargs "-o")]
                    (coll/vfold
                     (fn[oiv]
                       (infof "TNSEQ-AGGREGATE: %s"
                              (vec (concat theargs oiv)))
                       (let [ret (apply
                                  pg/perl script
                                  (vec (concat theargs oiv
                                               [{:verbose true :throw false}])))
                             exit-code @(ret :exit-code)
                             exit (if (= 0 exit-code) :success exit-code)
                             err (-> (ret :stderr) (str/split #"\n") last)]
                         {:name "tnseq-aggregate"
                          :value oiv
                          :exit exit
                          :err err}))
                     (conj pairs (coll/concatv [allcsv] allins))))))
              grps))))

 ;; Cardinalities of arguments
 ;;
 :argcard {"-m" 1, "-w" 1, "-x" 1
           "-l" 1, "-b" 1, "-c" 1
           "-o" 1}

 :description
 "Function wrapping aggregate.pl."
}
