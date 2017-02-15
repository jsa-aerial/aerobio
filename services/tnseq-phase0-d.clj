
{:name "tnseq-phase0-d",
 :path "",

 :graph {:coll {:type "func"
                :name "collapser"}
         :mail {:type "func"
                :name "mailit"
                :args ["#2"         ; recipient
                       "Aerobio job status: tnseq phase-0d"
                       "Finished"]} ; subject, body intro

         :edges {:coll [:mail]}}

 :description "Basically, Tn-Seq collapser. Unclear if this is really needed as the collapse node is now part of phase-0*"
 }
