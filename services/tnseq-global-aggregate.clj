
{
 :name "tnseq-global-aggregate",
 :path "",
 :func (fn [eid aggrfile data]
         (infof "Tn-Seq global aggr, data : %s" data)
         (when (and (pg/eoi? data) (pg/done? data))
           (infof "Tn-Seq aggregate starting... %s" data)
           (let [script (fs/join (fs/pwd) "Scripts" "aggregate.pl")
                 aggr-tuples (htts/get-aggregate-files eid aggrfile)
                 refdir (cmn/get-exp-info eid :refs)]
             (coll/vfold
              (fn[tuple]
                (let [[ins ot bnum] tuple
                      refnm (-> ins first fs/basename
                                (str/split #"-") first
                                ((cmn/get-exp-info eid :ncbi-sample-xref)))
                      refgbk (fs/join refdir (str refnm ".gbk"))
                      norm (fs/join refdir "NormGenes" (str refnm ".txt"))
                      ret (apply
                           pg/perl script
                           (concat ["-m" norm "-w" "1" "-x" "10"
                                    "-l" "50" "-b" bnum "-c" refgbk "-o" ot]
                                   ins [{:verbose true :throw false}]))
                      exit-code @(ret :exit-code)
                      exit (if (= 0 exit-code) :success exit-code)
                      err (-> (ret :stderr) (str/split #"\n") last)]
                  {:name "tnseq-aggregate"
                   :value tuple
                   :exit exit
                   :err err}))
              aggr-tuples))))

 :description "Compute global aggregate on tuples [ins ot bnum]. where ins are the set of fitness csvs to aggregate into out csv file ot using bottleneck number bnum. Typically used as post aggregation for in-vivo analysis."
}
