{
 :name "run-featureCounts",
 :path "",

 :func (fn [& args]
         (let [ret (apply pg/featureCounts
                          (concat args
                                  [{:verbose true :throw false}]))
               exit-code @(ret :exit-code)
               exit (if (= 0 exit-code) :success exit-code)
               err (-> (ret :stderr) (str/split #"\n") last)]
           {:name "featureCounts"
            :value (let [[_ csv & bams] (coll/drop-until #(= % "-o") args)]
                     [bams csv])
            :exit exit
            :err err}))


 :description "Produce 'feature' counts for each pair in the comparison set determined by eid (#1 arg), the given comparison file for eid (#2 arg) and whether replicates are used (#3 arg). Requires a gtf (#5 arg) matching the bam reference gbk and feature-type in gtf (#4 arg 'gene', 'CDS', et.al.)."
 }
