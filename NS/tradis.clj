
(ns aerobio.htseq.tradis
  [:require
   [clojure.string :as str]

   [aerial.fs :as fs]
   [aerial.utils.string :as astr]
   [aerial.utils.coll :as coll]
   [aerial.utils.io :refer [letio] :as io]
   [aerial.utils.math :as m]
   [aerial.utils.math.infoth :as it]
   [aerial.bio.utils.files :as bufiles]

   [aerobio.params :as pams]
   [aerobio.htseq.common :as cmn]
   [aerobio.pgmgraph :as pg]])

(print-str *ns*)

(defn get-gDNA
  ([sq]
   (get-gDNA sq "CCGGGGACTTATCAGCCAACCTGT" 4 24))
  ([sq pattern within-cnt extract-cnt]
    (let [patlen (count pattern)]
      (loop [i 0
             patterns (astr/sliding-take 1 patlen sq)]
        (let [curpat (first patterns)
              found (= curpat pattern)
              start (+ i patlen)
              end (+ start extract-cnt)]
          (if (or found (> i within-cnt))
            [found curpat i (subs sq start end)]
            (recur (inc i) (rest patterns))))))))

[(let [sq "AAACCGGGGACTTATCAGCCAACCTGTTATGCTGCGGTGTATTGAAGTCAGGCTCGCCTGCTCCTAAG"]
  (get-gDNA sq))
 (let [ns (ns-name *ns*)]
   [ns :as "tradis"])]

