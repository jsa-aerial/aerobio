{
 :name "bc2fq",
 :path "",
 :func (fn [eid & data]
         (let [exp-base (pams/get-params :exp-base)
               expdir (fs/join exp-base eid)
               make (cmn/get-exp-info eid :instrument-make)
               opts {:illum {:params (conj ["--no-lane-splitting"
                                            "--runfolder-dir"] expdir)
                             :converter :bcl2fastq}
                     :elembio {:params [expdir (fs/join expdir "Data")
                                        "--no-projects" "--group-fastq"
                                        "--num-threads" "32"]}}]
           (infof "BC2FQ on Exp %s" eid)
           (pg/bc2fq make opts)
           eid))
 ;; instructional data used in /help
 :description  "Function wrapping utility for converting, via dispatch on make, binary files to fastqs",
}
