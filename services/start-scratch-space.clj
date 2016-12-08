
{
 :name "start-scratch-space",
 :path "",
 :func (fn [eid & data]
         (if (pg/eoi? eid)
           (pg/done)
           (do
             (infof "Create scratch space / move fastqs for Exp %s" eid)
             (htrs/start-scratch-space eid)
             eid)))
 ;; instructional data used in /help
 :description "Create scratch directory for experiment EID and move converted fastq.gz files to staging there.",
}
