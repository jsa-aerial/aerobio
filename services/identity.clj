
{
 :name "identity",
 :path "",
 :func (fn
         ([arg1 & args]
          (infof "Identity passing through args %s" (cons arg1 args))
          (cons arg1 args))
         ([arg]
          (when (not= arg ::pg/done)
            (infof "Identity passing through arg %s" arg)
            arg)))
 ;; instructional data used in /help
 :description "Return arg or args unchanged",
}
