
{
 :name "set-exp",
 :path "",
 :func (fn [eid & data]
         (if (pg/eoi? eid)
           (pg/done)
           (do
             (infof "Set exp db for %s" eid)
             (cmn/set-exp eid)
             eid)))
 ;; instructional data used in /help
 :description "Define and set the experimental database values for experiement with id EID.",
}
