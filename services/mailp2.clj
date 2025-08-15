
{
 :name "mailp2",
 :path "",
 :func (fn [eid recipient subject intro result-maps]
         (cond
           (pg/eoi? result-maps) nil ; (pg/done) is sent on ch close!

           (every? map? result-maps)
           (let [[ibase obase msgs] (cmn/resultset->msgset result-maps)
                 overall (reduce (fn[R x]
                                   (cond (= x R :success) R
                                         (= x R :failed) R
                                         :else :mixed))
                                 (map first msgs))
                 msg (->> msgs
                          (cons (str "Output Base '" obase "'"))
                          (cons (str "Input Base '" ibase "'"))
                          (cons (str "Overall " overall))
                          (cons intro)
                          (map str) (str/join "\n"))]
             (pg/send-msg eid [recipient] subject msg))

           :else
           (let [msg (->> result-maps (cons intro) (map str) (str/join "\n"))]
             (pg/send-msg eid [recipient] subject msg))))

 :description "Mailer function node for phase2/gen-feature-counts. recipient is user account that requested the associated job this node is final pt of DAG, subject is type of job, msg is job finish details."
}
