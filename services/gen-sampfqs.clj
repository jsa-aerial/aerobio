
{
 :name "gen-sampfqs",
 :path "",
 :func (fn [eid & data]
         (if (pg/eoi? eid)
           (pg/done)
           (let [exp (cmn/get-exp-info eid :exp)]
             (infof "Gen experiment sample fastqs for %s" eid)
             ;; Dispatch to exp specific processing
             (cmn/gen-sampfqs exp eid)
             eid)))

 :description "Uses experiment id EID to obtain set of initial fastqs for experiment and then generates the corresponding sub-read sample fastqs. In the case of tech replicates, splits them by experiment sample barcodes and filters these for quality, then writes new fastqs for all replicates.",
}
