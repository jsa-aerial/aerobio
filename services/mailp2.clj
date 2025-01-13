
{
 :name "mailp2",
 :path "",
 :func (fn [eid recipient subject intro result-maps]
         (cond
           (pg/eoi? result-maps) nil ; (pg/done) is sent on ch close!

           (every? map? result-maps)
           (let [base (->> result-maps first :value second fs/dirname)
                 msgs (for [retmap result-maps]
                        (let [name (retmap :name)
                              [bams csv] (retmap :value)
                              bams (if (coll? bams)
                                     (map fs/basename bams)
                                     (fs/basename bams))
                              csv (if (coll? csv)
                                    (map fs/basename csv)
                                    (fs/basename csv))
                              exit (retmap :exit)
                              err (retmap :err)]
                          (if (= exit :success)
                            [exit csv]
                            [exit err [bams csv]])))
                 overall (reduce (fn[R x]
                                   (cond (= x R :success) R
                                         (= x R :fail) R
                                         :else :mixed))
                                 (map first msgs))
                 msg (->> msgs
                          (cons (str "Base '" base "'"))
                          (cons (str "Overall " overall))
                          (cons intro)
                          (map str) (str/join "\n"))]
             (pg/send-msg eid [recipient] subject msg))

           :else
           (let [msg (->> result-maps (cons intro) (map str) (str/join "\n"))]
             (pg/send-msg eid [recipient] subject msg))))

 :description "Mailer function node for phase2/gen-feature-counts. recipient is user account that requested the associated job this node is final pt of DAG, subject is type of job, msg is job finish details."
}
