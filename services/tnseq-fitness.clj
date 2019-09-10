
{
 :name "tnseq-fitness",
 :path "",
 :func (fn [eid compfile]
         (let [script (fs/join (fs/pwd) "Scripts" "calc_fitness")
               cmp-quads (cmn/get-comparison-files :tnseq eid compfile false)
               refdir (cmn/get-exp-info eid :refs)]
           (coll/vfold
            (fn[[t1 t2 csv ef :as cmp-quad]]
              (let [strain (-> t1 fs/basename (str/split #"-") first)
                    refnm  ((cmn/get-exp-info eid :ncbi-sample-xref) strain)
                    gtf (fs/join refdir (str refnm ".gtf"))
                    norm   (fs/join refdir "NormGenes" (str refnm ".txt"))
                    main ["-expansion" ef
                          "-normalize" norm "-ref" gtf
                          "-t1" t1 "-t2" t2 "-out" csv
                          "-out2" (fs/replace-type csv "-norm-info.txt")
                          {:verbose true :throw false}]
                    argcard ((get-toolinfo "tnseq-fitness" eid) :argcard)
                    defaults ["-ef" ".0" "-el" ".10" "-cutoff" "0"]
                    cmdargs ((cmn/get-exp-info eid :cmdsargs) "calc_fitness" {})
                    optargs (pg/merge-arg-sets argcard defaults cmdargs)
                    theargs (concat optargs main)
                    ret (apply pg/python script theargs)
                    exit-code @(ret :exit-code)
                    exit (if (= 0 exit-code) :success exit-code)
                    err (-> (ret :stderr) (str/split #"\n") last)]
                {:name "tnseq-fitness"
                 :value cmp-quad
                 :exit exit
                 :err err}))
            cmp-quads)))

 ;; Cardinalities of arguments
 ;;
 :argcard {"-ef" 1, "-el" 1,
           "-cutoff" 1, "-cutoff2" 1, "-expansion" 1,
           "-features" 1, "-d" 0, "-reads1" 1, "-reads2" 1
           "-strand" 1, "-reversed" 1, "-maxweight" 1, "-multiply" 1
           "-normalize" 1, "-ref" 1, "-wig" 1, "-uncol" 0
           "-t1" 1, "-t2" 1, "-out" 1, "-out2" 1}

 :description "Function wrapping calc_fitness.py taking cmp-quads [t1 t2 csv ef] and gtf. gtf is a gene-transfer-format reference annotation file for the strain."
}
