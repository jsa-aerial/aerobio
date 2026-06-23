
{:nodes
 {:align-fcount
  {:name "align-fcount",
   :type "tool",
   :args []}
  :prn1 {:type "func",
         :name "prn"}},
 :root :align-fcount
 :edges {:align-fcount [:prn1]}
 :cli {:usage ["align-fcount <options> dirdir"
               "Bulk fastq alignment and feature counting"
               "\ndirdir is a directory of subdirectories."
               "Each subdirectory should be named after an official reference,"
               "or a lab id listed in the `idrefmap` option with an associated"
               "official reference name."
               "\nOptions:"]
       :prefn (fn[arglist]
                (let [[idrefmap user dirdir] arglist
                      _ (infof "IRmap '%s', User '%s', DirDir '%s'"
                               idrefmap user dirdir)
                      errs (list)
                      errs (cond
                             (nil? dirdir)
                             errs

                             (-> dirdir fs/directory? not)
                             (cons (format "'%s' does not exist" dirdir) errs)

                             (nil? idrefmap)
                             errs

                             (not (->> idrefmap (fs/join dirdir) fs/file?))
                             (cons (format "'%s' is not a file in '%s'"
                                           idrefmap dirdir)
                                   errs)

                             :else errs)

                      refdir (pams/get-params [:refdata :refdir])
                      refmap (when (and dirdir idrefmap (not (seq errs)))
                               (bac/get-refmap dirdir idrefmap user))
                      refvalvec (when refmap
                                  (mapv (fn[[id ent]]
                                          (let [ref (ent :ref)]
                                            [ref (->> (str ref ".gtf")
                                                      (fs/join refdir)
                                                      fs/file?)]))
                                        (bac/get-todoents refmap)))
                      errs (if refvalvec
                             (reduce
                              (fn[errs [n good?]]
                                (if good?
                                  errs
                                  (cons (format
                                         "'%s' is not a registered reference"
                                         n)
                                        errs)))
                              errs refvalvec)
                             errs)]
                  (when (seq errs) errs)))

       :args ["dirdir"]

       :options
       [["-m" "--idrefmap IdRefMap" "Common Id to official RefName map"
         :missing "The idrefmap file name in dirdir is required"]
        ["-u" "--user UserAccnt" "User account (zulip/email) for result msg"
         :missing "The user account is required"]]
       :order [:idrefmap :user]}}
