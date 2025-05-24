
{:nodes
 {:split
  {:name "split-output",
   :type "tool",
   :args []}
  :prn1 {:type "func",
         :name "prn"}},
 :root :split
 :edges {:split [:prn1]}
 :cli {:usage "split-output <options> expdir datadir"
       :options
       [["-u" "--user USERACCNT" "User account (zulip/email) for result msg"
         :missing "The user account is required"]
        ["-e" "--eid EID" "The Experiment ID of run"
         :missing "The Experiment ID is required"]]
       :order [:user :eid]}}
