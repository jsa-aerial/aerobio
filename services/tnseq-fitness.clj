
{
 :name "tnseq-fitness",
 :path "",
 :func (fn [eid compfile]
         (let [script (fs/join (fs/pwd) "Scripts" "calc_fitness.py")
               cmp-quads (cmn/get-comparison-files :tnseq eid compfile false)
               refdir (cmn/get-exp-info eid :refs)]
           (coll/vfold
            (fn[[t1 t2 csv ef :as cmp-quad]]
              (let [strain (-> t1 fs/basename (str/split #"-") first)
                    refnm  ((cmn/get-exp-info eid :ncbi-sample-xref) strain)
                    gtf (fs/join refdir (str refnm ".gbk"))
                    norm   (fs/join refdir "NormGenes" (str refnm ".txt"))
                    ret (pg/python
                         script
                         "-ef" ".0" "-el" ".10" "-cutoff" "0"
                         "-expansion" ef
                         "-normalize" norm "-ref" gtf
                         "-t1" t1 "-t2" t2 "-out" csv
                         "-out2" (fs/replace-type csv "-norm-info.txt")
                         {:verbose true :throw false})
                    exit-code @(ret :exit-code)
                    exit (if (= 0 exit-code) :success exit-code)
                    err (-> (ret :stderr) (str/split #"\n") last)]
                {:name "tnseq-fitness"
                 :value cmp-quad
                 :exit exit
                 :err err}))
            cmp-quads)))

 :description "Function wrapping calc_fitness.py taking cmp-quads [t1 t2 csv ef] and gtf. gtf is a gene-transfer-format reference annotation file for the strain."
}
