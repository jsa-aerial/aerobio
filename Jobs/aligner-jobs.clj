
{:bowtie2
 {:nodes
  {:root
   {:name "bowtie2-align",
    :type "tool",
    :args []}
   :prn1 {:type "func",
          :name "prn"}},
  :edges
  {:root [:prn1]}}

 :STAR
 {:nodes
  {:root
   {:name "star-align",
    :type "tool",
    :args []}
   :prn1 {:type "func",
          :name "prn"}},
  :edges
  {:root [:prn1]}}
 }
