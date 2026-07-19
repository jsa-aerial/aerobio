(in-ns 'aerobio.htseq.wgseq)




(defmethod cmn/resultset->msgset "breseq-runs"
  [result-maps]
  (let [[fqs outdir] (->> result-maps first :value)
        inbase (-> fqs first fs/dirname)
        outbase (fs/dirname outdir)
        msgs (for [retmap result-maps]
               (let [name (retmap :name)
                     [fqs outdir] (retmap :value)
                     fqs (mapv fs/basename fqs)
                     exit (retmap :exit)
                     err (retmap :err)]
                 (if (= exit :success)
                   [exit fqs outdir]
                   [exit err [fqs outdir]])))]
    [inbase outbase msgs]))








;;; for auto ns require to server
(let [ns (ns-name *ns*)]
  [ns "htws"])
