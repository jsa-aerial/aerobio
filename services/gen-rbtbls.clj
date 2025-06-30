
{:name "gen-rbtbls"
 :path ""
 :func (fn [eid chksiz maxn delta minrds]
         (rbtnseq/gen-rbtnseq-xreftbls eid chksize maxn delta minrds))

 :description "service indirection to main rbtnseq tbl builder function"}
