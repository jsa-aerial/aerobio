(in-ns 'aerobio.htseq.tnseq)




(defmethod cmn/resultset->msgset "tnseq-fitness"
  [result-maps]
  (let [[m1 m2 fitcsv ef] (->> result-maps first :value)
        fbase (-> fitcsv second fs/dirname)
        mbase (-> m1 second fs/dirname)
        msgs (for [retmap result-maps]
               (let [name (retmap :name)
                     [m1 m2 fitcsv] (->> :value retmap
                                         (take 3)
                                         (mapv fs/basename))
                     exit (retmap :exit)
                     err (retmap :err)]
                 (if (= exit :success)
                   [exit fitcsv]
                   [exit err [m1 m2]])))]
    [mbase fbase msgs]))


(defmethod cmn/resultset->msgset "tnseq-aggregate"
  [result-maps]
  (let [[fulloiv & single-oivs] (->> result-maps first :value)
        abase (-> fulloiv first fs/dirname)
        fbase (-> single-oivs first second fs/dirname)
        msgs (for [retmap result-maps]
               (let [name (retmap :name)
                     [fulloiv & single-oivs] (->> :value retmap
                                                  (mapv #(mapv fs/basename %)))
                     exit (retmap :exit)
                     err (retmap :err)]
                 (if (= exit :success)
                   [exit (first fulloiv)]
                   [exit err (-> fulloiv rest vec)])))]
    [fbase abase msgs]))
