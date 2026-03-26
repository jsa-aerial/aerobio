
{:nodes
 {:make
  {:name "make-refdata",
   :type "tool",
   :args []}
  :prn1 {:type "func",
         :name "prn"}},
 :root :make
 :edges {:make [:prn1]}
 :cli {:usage ["make-refdata <options> namelist gbkdir fastadir"
               "\nAll three arguments are required\n"
               "namelist is a comma separated list of gbk names"
               "gbkdir is the directory containing the named gbks"
               "fastadir is the directory to write the genome fasta files"
               "\nOptions:"
               "**note** -m and -r must be in the same order as namelist\n"]
       :prefn (fn[arglist]
                (let [[multi regex user namelist gbkdir fastadir] arglist
                      errs (list)
                      multi (-> multi (str/split #",")
                                (->> (mapv #(fs/replace-type % ""))))
                      regex (-> regex (str/split #","))
                      names (-> namelist (str/split #",")
                                (->> (mapv #(fs/replace-type % "")) (into #{})))
                      errs (if (not= (count multi) (count regex))
                             (cons "Count of -m and -r options must match" errs)
                             errs)
                      errs (if (not (every? names multi))
                             (cons "-m names must match namelist names!" errs)
                             errs)]
                  (when (seq errs) errs)))
       :args ["namelist" "gbkdir" "fastadir"]
       :options
       [["-m" "--multiloc MULTILOC"
         "comma separated subset of 'namelist' gbks with multiple loci"
         :default ""]
        ["-r" "--regex LOCUSREGX"
         "comma separated list of regex for LOCUS fields for each -m name"
         :default ""]
        ["-u" "--user USERACCNT" "User account (zulip/email) for result msg"
         :default "NONE"]]
       :order [:multiloc :regex :user]}}

