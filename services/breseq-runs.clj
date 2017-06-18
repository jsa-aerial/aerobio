
{
 :name "breseq-runs",
 :path "",
 :func (fn [quads]
         (if (pg/eoi? quads)
           (pg/done)
           (->>
            quads
            ;;fire off a set of (count quads) breseq runs
            (mapv
             (fn[quad]
               (future
                 (let [q1 quad
                       [[q1r1 q1r2] q1-refgbk q1-outdir] q1
                       poly (if (re-find #"clone" q1-outdir) [] ["-p"])
                       ret (apply pg/breseq
                                  (concat poly ["-j" "4"
                                                "-o" q1-outdir
                                                "-r" q1-refgbk
                                                q1r1 q1r2]
                                          [{:verbose true :throw false}]))
                       exit-code @(ret :exit-code)
                       exit (if (= 0 exit-code) :success exit-code)
                       err (-> (ret :stderr) (str/split #"\n") last)]
                   {:name "breseq-runs"
                    :value [[q1r1 q1r2] q1-outdir]
                    :exit exit
                    :err err}))))
            ;; Wait for them all before proceeding
            ;; this suffers from partition problems but ensures saner
            ;; machine resource usage
            (mapv (fn[fut] (deref fut))))))

 :description "Run breseq on WG data (from wgseq-phase0) using polymorphic mode for non-clonal data and consensus mode for straight population data. Currently uses defaults for the switches controlling the processing for these. In particular, does not use -l switch which limits read depth! A quad [r1 r2 refgbk otdir] consists of the R1 and R2 paired reads, the reference gbk and the output directory."
}
