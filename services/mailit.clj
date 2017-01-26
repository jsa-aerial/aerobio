

{
 :name "mailit",
 :path "",
 :func (fn [recipient subject msg]
         (if (pg/eoi? msg)
           (pg/done)
           (pg/send-msg [recipient] subject msg)))

 :description "General mailer function node. recipient is user account that requested the associated job this node is final pt of DAG, subject is type of job, msg is job finish details."
}
