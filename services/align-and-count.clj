
{:name "align-and-count"
 :path ""

 :func
 (fn [jobinfo]
   (infof "AlignAndCount JobInfo '%s'" jobinfo)
   (if (pg/eoi? jobinfo)
     (pg/done)
     (let [[ent get-toolinfo alnjobs fctjobs] jobinfo
           status (atom {})
           alnfutvs (mapv (fn[alnjob]
                            (-> alnjob (pg/config-pgm-graph-nodes
                                        get-toolinfo nil nil)
                                pg/config-pgm-graph
                                pg/make-flow-graph
                                pg/run-flow-program))
                          alnjobs)
           jobresv (mapv #(cmn/job-flow-node-results % status) alnfutvs)]
       (prn status)
       (if (reduce (fn[TV b] (or TV b)) jobresv)
         [{:name (-> alnjobs first :name)
           :value (mapv #(-> % (get-in [:nodes :root :args])
                             (->> (drop 2)))
                        alnjobs)
           :exit :failed
           :err (status :done)}]
         (let [fctfutvs (mapv (fn[fctjob]
                                (-> fctjob (pg/config-pgm-graph-nodes
                                            get-toolinfo nil nil)
                                    pg/config-pgm-graph
                                    pg/make-flow-graph
                                    pg/run-flow-program))
                              fctjobs)
               jobresv (mapv #(cmn/job-flow-node-results % status) fctfutvs)
               fctresv (mapv (fn[fctfutv]
                               (->> fctfutv
                                    (filter #(-> % deref :request :name
                                                 (str/starts-with? "run-fea")))
                                    first deref :result))
                             fctfutvs)]
           (prn status)
           fctresv)))))

 :description ""
 }

;;; (->> fctfut (filter #(-> % deref :request :name (str/starts-with? "run-fea"))) first deref :result)
