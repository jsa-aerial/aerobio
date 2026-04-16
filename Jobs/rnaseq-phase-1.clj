
{:bowtie2
 {:nodes
  {:ph1
   {:name "rnaseq-phase1",
    :type "tool",
    :args []}
   :prn1 {:type "func",
          :name "prn"}},
  :edges
  {:ph1 [:prn1]}}
 :STAR
 {:nodes
  {:ph1
   {:name "rnaseq-star-phase1",
    :type "tool",
    :args []}
   :prn1 {:type "func",
          :name "prn"}},
  :edges
  {:ph1 [:prn1]}}
 }
