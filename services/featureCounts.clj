
{
 :name "featureCounts",
 :path "",
 :func (fn [feature-type gtf bams-csv-pair]
         (if (pg/eoi? bams-csv-pair)
           (pg/done)
           (let [[bams csv] bams-csv-pair
                 ret (apply pg/featureCounts
                            (concat ["-a" gtf "-o" csv "-t" feature-type]
                                    bams [{:verbose true :throw false}]))
                 exit-code @(ret :exit-code)
                 exit (if (= 0 exit-code) :success exit-code)
                 err (get-in ret [:proc :err])]
             {:name "featureCounts"
              :value bams-csv-pair
              :exit exit
              :err (mapv identity err)})))

 :description "Function wrapping featureCounts taking bams-csv-pair file. gtf is a gene-transfer-format annotation and must be sorted to match the bam files and feature-type is the feature class of the gtf (gene, CDS, exon, ...)"
}
