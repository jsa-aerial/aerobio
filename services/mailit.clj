

{
 :name "mailit",
 :path "",
 :func (fn [eid recipient subject intro result]
         (cond
           (pg/eoi? result) (pg/done)

           :else
           (let [msg (->> result list
                          (cons intro)
                          (map str)
                          (str/join "\n"))]
             (pg/send-msg eid [recipient] subject msg))))

 :description "Simple mailer function node. recipient is user account that requested the associated job this node is final pt of DAG, subject is type of job, msg is job
 finish details."
}

