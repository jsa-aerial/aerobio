{
 :name "bc2fq",
 :path "",
 :func (fn
         ([eid]
          (let [exp-base (pams/get-params :exp-base)
                expdir (fs/join exp-base eid)
                make (cmn/get-instrument-make eid)
                opts {:illum {:params (conj ["--no-lane-splitting"
                                             "--runfolder-dir"] expdir)
                              :converter :bcl2fastq}
                      :elembio {:params [expdir (fs/join expdir "Data")
                                         "--no-projects" "--group-fastq"
                                         "--num-threads" "32"]}}]
            (infof "BC2FQ on Exp %s" eid)
            (pg/bc2fq make opts)
            eid))
         ([eid base-dir data-dir & args]
          (let [exp-base (pams/get-params :exp-base)
                expdir (fs/join exp-base eid)
                make (cmn/get-instrument-make eid)]
            (infof "BC2FQ on base '%s', data dir '%s';\n args '%s'"
                   base-dir data-dir args)
            (apply pg/bc2fq make expdir (fs/join expdir "Data") args)
            eid)))
 ;; instructional data used in /help
 :description  "Function wrapping utility for converting, via dispatch on make, binary files to fastqs",
}
