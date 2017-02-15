
{
 :name "collapser",
 :path "",
 :func (fn [eid & _]
         (if (pg/eoi? eid)
           (pg/done)
           (let [pairgrps (htts/get-collapse-groups eid)]
             (mapv #(reduce
                     (fn[E stat]
                       (cond (= :success E (stat :exit)) :success
                             (= :success (stat :exit))   :mixed
                             :else :failed))
                     :success %)
                   (mapv (fn[pairs]
                           (htts/run-collapse-group pairs))
                         pairgrps)))))

 :description "Collapse duplicate sequences for each file (fq or fa) in pairs. Takes the experiement id and generates the pair groups and runs parallel collapser on each such group. pairs is a collection of vectors [fqa fa] where fqa is a fastq or fasta file (gzipped or not) and fa is the output fasta file (it will be gzipped). Sequences are collapsed to records of single instance with counts in id line."
}
