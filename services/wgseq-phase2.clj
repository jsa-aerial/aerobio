
{
 :name "wgseq-phase2",
 :path  "",

 :graph {:dir  {:type "func"
                :name "get-comparison-files"
                :args ["#1", "#2" "#3"]} ; eid, comparison-file, :NA here
         :bseq {:type "func"
                :name "sfj-breseq-runs"}
         :mail {:type "func"
                :name "mailp2"
                :args ["#1"              ; eid
                       "#4"              ; recipient
                       "Aerobio job status: wgseq phase-2"
                       "Finished"]}      ; subject, body intro
         :edges {:dir  [:bseq]
                 :bseq [:mail]}}

 ;; instructional data used in /help
 :description "Run a full set of breseq runs for experiment '#1' EID, with comparison file '#2' and sending completion msg to recipient '#4'. Uses streaming 'fork/join' breseq runner, which is also able to restart run after last completed run in case of server interruptions."
 }
