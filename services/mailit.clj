
{
 :name "mailit",
 :path "",
 :func (fn [recipient subject intro result-maps]
         (cond
           (pg/eoi? result-maps) (pg/done)

           (every? map? result-maps)
           (let [msgs (for [retmap result-maps]
                        (let [name (retmap :name)
                              [bams csv] (retmap :value)
                              bams (map fs/basename bams)
                              csv (fs/basename csv)
                              exit (retmap :exit)
                              err (retmap :err)]
                          (if (= exit :succss)
                            [exit csv]
                            [exit err [bams csv]])))
                 msg (->> msgs (cons intro) (map str) (str/join "\n"))]
             (pg/send-msg [recipient] subject msg))

           :else
           (let [msg (->> result-maps (cons intro) (map str) (str/join "\n"))]
             (pg/send-msg [recipient] subject msg))))

 :description "General mailer function node. recipient is user account that requested the associated job this node is final pt of DAG, subject is type of job, msg is job finish details."
}
