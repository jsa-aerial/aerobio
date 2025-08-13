(in-ns 'aerobio.htseq.rnaseq)




(defmethod cmn/resultset->msgset "deseq2-rnaseq"
  [result-maps]
  (let [[fcnt-dir fcnt-csv dge-dir dge-csv] (->> result-maps first :value)
        msgs (for [retmap result-maps]
               (let [name (retmap :name)
                     [fcntdir fcnt-csv dge-dir dge-csv] (retmap :value)
                     exit (retmap :exit)
                     err (retmap :err)]
                 (if (= exit :success)
                   [exit dge-csv]
                   [exit err [fcnt-csv]])))]
    [fcnt-dir dge-dir msgs]))
