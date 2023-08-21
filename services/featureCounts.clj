
{
 :name "featureCounts",
 :path "",
 :func (fn [eid feature-type _ bams-csv-pair]
         (if (pg/eoi? bams-csv-pair)
           (pg/done)
           (let [[bams csv] bams-csv-pair
                 strain (-> bams first fs/basename (str/split #"-") first)
                 refnm ((cmn/get-exp-info eid :ncbi-sample-xref) strain)
                 gtf (fs/join (cmn/get-exp-info eid :refs) (str refnm ".gtf"))
                 main ["-a" gtf, "-o" csv]
                 argcard ((get-toolinfo "featureCounts" eid) :argcard)
                 defaults ["-t" feature-type]
                 cmdargs ((cmn/get-exp-info eid :cmdsargs) "featureCounts" {})
                 optargs (pg/merge-arg-sets argcard defaults cmdargs)
                 theargs (concat main optargs)

                 ret (apply pg/featureCounts
                            (concat theargs
                                    bams
                                    [{:verbose true :throw false}]))
                 exit-code @(ret :exit-code)
                 exit (if (= 0 exit-code) :success exit-code)
                 err (-> (ret :stderr) (str/split #"\n") last)]
             {:name "featureCounts"
              :value bams-csv-pair
              :exit exit
              :err err})))

 ;; Cardinalities of arguments
 ;;
 :argcard {"-a" 1, "-t" 1,
           "-o" 1, "-R" 1
           "-p" 0, "-L" 0}

 :description "Function wrapping featureCounts taking bams-csv-pair file. gtf is a gene-transfer-format annotation and must be sorted to match the bam files and feature-type is the feature class of the gtf (gene, CDS, exon, ...)"
}
