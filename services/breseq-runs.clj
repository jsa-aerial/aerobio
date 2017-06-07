
{
 :name "breseq-runs",
 :path "",
 :func (fn [quad]
         (if (pg/eoi? quad)
           (pg/done)
           (let [q1 quad
                 [[q1r1 q1r2] q1-refgbk q1-outdir] q1
                 ;;[[q2r1 q2r2] q2-refgbk q2-outdir] q2
                 poly (if (re-find #"clone" q1-outdir) [] ["-p"])
                 ret (apply pg/breseq
                            (concat poly ["-j" "16" "-l" "60"
                                          "-o" q1-outdir "-r" q1-refgbk
                                          q1r1 q1r2]
                                    [{:verbose true :throw false}]))
                 exit-code @(ret :exit-code)
                 exit (if (= 0 exit-code) :success exit-code)
                 err (-> (ret :stderr) (str/split #"\n") last)]
             {:name "breseq-runs"
              :value [[q1r1 q1r2] q1-outdir]
              :exit exit
              :err err})))

 :description "Run breseq on WG data (from wgseq-phase0) using polymorphic mode for non-clonal data and consensus mode for straight population data. Currently uses defaults for the switches controlling the processing for these."
}
