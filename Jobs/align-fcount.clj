
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
               "Each subdirectory contains fastqs to be processed as defined"
               "in the `idrefmap` option manifest CSV file in dirdir."
               "\nThe manifest file has columns:"
               "| id | ref | aligner | alnopts | fcopts |"
               "* id is the name of a subdirectory of dirdir"
               "* ref is the official reference name (eg, NC_003028)"
               "* aligner is the command line name of aligner to use"
               "* alnopts are non I/O option flags to aligner"
               "* fcopts are non I/O option flags to feature counts"
               "\nOptions:"]
       :prefn (fn[arglist]
                (let [[idrefmap chunksize user dirdir] arglist
                      _ (infof "IRmap '%s', CkSize '%s', User '%s', DirDir '%s'"
                               idrefmap chunksize user dirdir)
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
       [["-m" "--idrefmap IdRefMap"
         "The file name of CSV manifest in dirdir to use"
         :missing "The idrefmap file name in dirdir is required"]
        ["-n" "--chunksize ChunkSize"
         "File chunk size to process in parallel: range [1-5], default 2"
         :default 2
         :parse-fn #(Integer/parseInt %)
         :validate [#(and (number? %) (<= 1 % 5)) "Must be an int in [1,5]"]]
        ["-u" "--user UserAccnt" "User account (zulip/email) for result msg"
         :missing "The user account is required"]]
       :order [:idrefmap :chunksize :user]}}
