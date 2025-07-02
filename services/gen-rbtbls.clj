
{:name "gen-rbtbls"
 :path ""
 :func (fn [eid chksz maxn delta minrds]
         (rbtnseq/gen-rbtnseq-xreftbls eid chksz maxn delta minrds))

 :description "service indirection to main rbtnseq tbl builder function"}
