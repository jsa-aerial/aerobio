
{
 :name "featureCounts",
 :path "",
 :func (fn [feature-type outdir gtf infile]
         (if (pg/eoi? infile)
           (pg/done)
           (let [outfile (fs/join
                          outdir
                          (->> infile fs/basename
                               (aerial.utils.string/split #"\.")
                               butlast (str/join ".") (#(str % ".csv"))))
                 ret (pg/featureCounts
                      "-a" gtf "-o" outfile "-t" feature-type infile
                      {:verbose true :throw false})
                 exit-code @(ret :exit-code)
                 exit (if (= 0 exit-code) :success exit-code)
                 err (get-in ret [:proc :err])]
             {:name "featureCounts"
              :value infile
              :exit exit
              :err (mapv identity err)})))

 :description "Function wrapping featureCounts taking infile of bam/sam file. gtf is a gene-transfer-format annotation and must be sorted to match bam/sam file, outdir is directory location for the output count table files (csvs), and feature-type is the feature class of the gtf (gene, CDS, exon, ...)"
}
