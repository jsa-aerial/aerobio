
{:name "align-and-count"
 :path ""

 :func
 (fn [jobinfo]
   (infof "AlignAndCount JobInfo '%s'" jobinfo)
   (if (pg/eoi? jobinfo)
     (pg/done)
     (let [[ent get-toolinfo alnjob fctjob] jobinfo
           status (atom {})
           alnfutv (-> alnjob (pg/config-pgm-graph-nodes
                               get-toolinfo nil nil)
                       pg/config-pgm-graph
                       pg/make-flow-graph
                       pg/run-flow-program)
           jobres (cmn/job-flow-node-results alnfutv status)]
       (if jobres
         {:name (alnjob :name)
          :value [(get-in alnjob [:nodes :root :args])]
          :exit :failed
          :err (jobres :done)}
         (let [fctfut (-> alnjob (pg/config-pgm-graph-nodes
                                  get-toolinfo nil nil)
                          pg/config-pgm-graph
                          pg/make-flow-graph
                          pg/run-flow-program)
               jobres (cmn/job-flow-node-results fctfut status)
               fctres (->> fctfut
                           (filter #(-> % deref :request :name
                                        (str/starts-with? "run-fea")))
                           first deref :result)]
           )))))

 :description ""
 }

;;; (->> fctfut (filter #(-> % deref :request :name (str/starts-with? "run-fea"))) first deref :result)
