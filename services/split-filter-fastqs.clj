
{
 :name "split-filter-fastqs",
 :path "",
 :func (fn [eid & data]
         (if (pg/eoi? eid)
           (pg/done)
           (let [exp (cmn/get-exp-info eid :exp)]
             (infof "Splitting and filtering fastqs by replicates for %s" eid)
             ;; This case dispatching is lame! Need to fix with mmethod
             (case exp
               :tnseq (htts/split-filter-fastqs eid)
               :rnaseq (htrs/split-filter-fastqs eid)
               :termseq (httm/split-filter-fastqs eid)
               :wgseq (htws/split-filter-fastqs eid))
             eid)))

 :description "Uses experiment id EID to obtain set of initial fastqs for experiment and then splits them by experiment sample barcodes and filters these for quality, then writes new fastqs for all replicates.",
}
