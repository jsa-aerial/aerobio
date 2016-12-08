{
 :apiVersion "0.1",
 :name "vcf read depther",
 :path  "vcfReadDepther",

 :graph {:i1 {:type :url}
         :bgzip {:type :tool :path "bgzip" :args ["-d"]}
         :vcfReadDepther {:type :tool :path "vcfReadDepther"}
         :edges {:bgzip [:vcfReadDepther] :i1 [:bgzip]}}

 ;; instructional data used in /help
 :description "quickly approximates read depth coverage data using tbi index",
 :exampleUrl "to add"
}
