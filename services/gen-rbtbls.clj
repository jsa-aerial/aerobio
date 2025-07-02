
{:name "gen-rbtbls"
 :path ""
 :func (fn [eid chksz maxn delta minrds]
         (infof "Start RB table generation: %s" eid)
         (rbtnseq/gen-rbtnseq-xreftbls eid chksz maxn delta minrds))

 :description "service indirection to main rbtnseq tbl builder function"}
