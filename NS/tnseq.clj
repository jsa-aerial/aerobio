(in-ns 'aerobio.htseq.tnseq)




(defmethod cmn/resultset->msgset "tnseq-fitness"
  [result-maps]
  (let [[m1 m2 fitcsv ef] (->> result-maps first :value)
        fbase (fs/dirname fitcsv)
        mbase (fs/dirname m1)
        msgs (for [retmap result-maps]
               (let [name (retmap :name)
                     [m1 m2 fitcsv] (->> :value retmap
                                         (take 3)
                                         (mapv fs/basename))
                     exit (retmap :exit)
                     err (retmap :err)]
                 (if (= exit :success)
                   [exit fitcsv]
                   [exit err ["Map inputs: " m1 m2]])))]
    [mbase fbase msgs]))


(defmethod cmn/resultset->msgset "tnseq-aggregate"
  [result-maps]
  (let [[aggr-csv & fitin-csvs] (->> result-maps first :value)
        abase (fs/dirname aggr-csv)
        fbase (-> fitin-csvs first fs/dirname)
        msgs (for [retmap result-maps]
               (let [name (retmap :name)
                     [aggr-csv & fitin-csvs] (->> :value
                                                  retmap
                                                  (mapv fs/basename))
                     exit (retmap :exit)
                     err (retmap :err)]
                 (if (= exit :success)
                   [exit aggr-csv]
                   [exit err ["Fitness input: " fitin-csvs]])))]
    [fbase abase msgs]))








;;; for auto ns require to server
(let [ns (ns-name *ns*)]
  [ns "htts"])
