
{:nodes
 {:rbtbls
  {:name "gen-rbtnseq-rbtbls",
   :type "func",
   :args []}
  :prn1 {:type "func",
         :name "prn"}},
 :root :rbtbls
 :edges {:rbtbls [:prn1]}
 :cli {:usage "gen-rbtnseq-rbtbls <options> eid"
       :options
       [["-u" "--user USERACCNT" "User account (zulip/email) for result msg"
         :missing "The user account is required"]

        ["-c" "--chksz BamCount" "Bam file count to process in parallel"
         :default 5
         :parse-fn #(Integer/parseInt %)
         :validate [#(and (number? %) (<= 1 % 7)) "Must be an int in [1,7]"]]

        ["-n" "--maxn DuplicateCutoff" "Max n in n->1 group processing"
         :default 5
         :parse-fn #(Integer/parseInt %)
         :validate [#(and (number? %) (<= % 7)) "Must be an int in [1,7]"]]

        ["-i" "--delta PosInterDelta" "Delta for position interval"
         :default 3
         :parse-fn #(Integer/parseInt %)
         :validate [#(and (number? %) (<= 1 % 5)) "Must be an int in [1,5]"]]

        ["-m" "--minrds MinGrpRds" "Minimum reads for a group to pass"
         :default 20
         :parse-fn #(Integer/parseInt %)
         :validate [#(and (number? %) (<= 10 %)) "Must be an integer > 10"]]]
       :order [:user :chksz :maxn :delta :minrds]}}

;;;--rcount-interval [0 5]
;;;-i --interval-delta 3
;;;-M --max-grp-size 5
;;;-m --min-grp-reads 20
