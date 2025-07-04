
{
 :name "split-filter-fastqs",
 :path "",
 :func (fn [eid & data]
         (if (pg/eoi? eid)
           (pg/done)
           (let [exp (cmn/get-exp-info eid :exp)]
             (infof "Splitting and filtering fastqs by replicates for %s" eid)
             ;; Dispatch to exp specific processing
             (cmn/gen-sampfqs exp eid)
             eid)))

 :description "Uses experiment id EID to obtain set of initial fastqs for experiment and then splits them by experiment sample barcodes and filters these for quality, then writes new fastqs for all replicates.",
}
