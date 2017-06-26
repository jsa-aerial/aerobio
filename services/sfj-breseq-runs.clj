
{
 :name "sfj-breseq-runs",
 :path "",
 :func (let [N 10
             quads (chan N)
             results (atom [])

             mkfut (fn[i]
                     (future
                       (infof "Fut (%s) starting ..." i)
                       (loop [q1 (<!! quads)]
                         (when q1
                           (if (fs/exists? (last q1))
                             (recur (<!! quads)) ; restart check
                             (let [[[q1r1 q1r2] q1-refgbk q1-outdir] q1
                                   _ (infof "Fut (%s),  %s start..."
                                            i q1-outdir)
                                   poly (if (re-find #"clone" q1-outdir)
                                          []
                                          ["-p"])
                                   ret (apply
                                        pg/breseq
                                        (concat poly ["-j" "4"
                                                      "-o" q1-outdir
                                                      "-r" q1-refgbk
                                                      q1r1 q1r2]
                                                [{:verbose true :throw false}]))
                                   exit-code @(ret :exit-code)
                                   exit (if (= 0 exit-code) :success exit-code)
                                   err (-> (ret :stderr) (str/split #"\n") last)
                                   retmap {:name "breseq-runs"
                                           :value [[q1r1 q1r2] q1-outdir]
                                           :exit exit
                                           :err err}]
                               (swap! results (fn[a] (conj a retmap)))
                               (infof "BRESEQ run (%s): '%s' finished ..."
                                      i (fs/basename q1-outdir))
                               (recur (<!! quads))))))
                       (infof "Fut (%s) Done, shutting down ..." i)
                       :chan-closed->done))

             ;; Effectively our thread pool. 10 futures (each on a
             ;; thread) which perform breseq run, then take from quad
             ;; channel for another set of run parameters. If channel
             ;; closed (no more input run data), future shuts down
             futs (volatile! :NYI)

             close-wait-return (fn[]
                                 (async/close! quads)
                                 (mapv deref @futs)
                                 (let [ret @results]
                                   (reset! results (pg/done))
                                   ret))]
         (fn [quad]
           (when (= @futs :NYI)
             (vswap! futs (fn[_] (mapv mkfut (range N)))))
           (if (pg/eoi? quad)
             (close-wait-return)
             (do (>!! quads quad)
                 (pg/need)))))

 :description "Streaming 'fork/join' breseq (sort of...) with restart capability. Run breseq on WG data (from wgseq-phase0) using polymorphic mode for non-clonal data and consensus mode for straight population data. Currently uses defaults for the switches controlling the processing for these. In particular, does not use -l switch which limits read depth! A quad [r1 r2 refgbk otdir] consists of the R1 and R2 paired reads, the reference gbk and the output directory."
}
