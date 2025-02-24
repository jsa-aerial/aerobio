
{:nodes
 {:split
  {:name "split-output",
   :type "tool",
   :args []}
  :prn1 {:type "func",
         :name "prn"}},
 :root :split
 :edges
 {:split [:prn1]}}
