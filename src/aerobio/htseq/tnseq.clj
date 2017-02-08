(ns iobio.htseq.tnseq
  [:require
   [clojure.string :as cljstr]

   [aerial.fs :as fs]
   [aerial.utils.string :as str]
   [aerial.utils.io :refer [letio] :as io]
   [aerial.utils.math.infoth :as it]

   [iobio.params :as pams]
   [iobio.htseq.common :as cmn]
   [iobio.pgmgraph :as pg]])


(defn trim-initsq-and-adapter
  "Trim off the 5' initiation sequence (in traditional Tn-Seq this is
  the 'HiSeq initiation' sequence) and the trailing 3' adapter
  sequence. Removing makes alignment more robust and simplifies
  transposon and barcode analysis. S is the starting base to
  keep (0-based) and E denotes the final base to keep, where E is
  typically given as a negative integer indicating how much to remove
  from the 3' end. In traditional Tn-Seq S is 9 while E is -17."
  [sq s e]
  (str/substring sq s e))

(defn position-transpose-prefix
  "Finds the 'best' match of tpn to the end of sq. Does not use
  optimal alignment scoring (edit distance), but a simplified Hamming
  distance with 'shrinking' window. Returns the index into sq of the
  best start location."
  [sq tpn]
  (let [mxtpn (count tpn)
        minsq (- (count sq) mxtpn)
        idxs0 (take (- mxtpn 2)
                    (iterate (fn[[x y]] [(inc x) (dec y)])
                             [minsq mxtpn]))
        maxsqi (->> idxs0 last first)]
    (loop [idxs idxs0]
      (let [[i j] (first idxs)
            m (if (< i maxsqi) 2 1)
            x (str/substring sq i (+ i j))
            y (str/substring tpn 0 j)]
        (cond (< (it/hamming x y) m) i
              (= i maxsqi) -1
              :else (recur (rest idxs)))))))

(defn seq-less-transposon
  [sq tpn]
  (let [i (position-transpose-prefix sq tpn)
        l (if (pos? i) (+ 2 i) i)]
    (when (pos? l)
      [(str/substring sq 0 l) l])))

(defn check-barcode
  [sq bc]
  (let [bcsz (count bc)]
    (->>  sq (str/sliding-take bcsz)
          (take 3)
          (map #(it/hamming bc %))
          (apply min) (>= 1))))

(defn trim-barcode
  [sq bcsz]
  (when sq
    (str/substring sq bcsz)))


(defn pre-pass [fqrec]
  (let [[id sq aux qc] fqrec
        sq (trim-initsq-and-adapter sq 9 -17)
        qc (trim-initsq-and-adapter qc 9 -17)
        i  (position-transpose-prefix sq "TAACAG")
        i  (if (pos? i) (+ 2 i) i)
        [sq qc] (when (pos? i)[(str/substring sq 0 i) (str/substring qc 0 i)])]
    (when (pos? i)
      [id sq aux qc])))

(defn split-filter-fastqs
  [eid]
  (cmn/split-filter-fastqs eid pre-pass))

(defn run-tnseq-phase-0
  [eid recipient get-toolinfo template]
  (let [rnas0 template
        cfg (-> (assoc-in rnas0 [:nodes :tsqp0 :args] [eid recipient])
                (pg/config-pgm-graph-nodes get-toolinfo nil nil)
                pg/config-pgm-graph)
        ;;_ (clojure.pprint/pprint cfg)
        futs-vec (->> cfg pg/make-flow-graph pg/run-flow-program)]
    (mapv (fn[fut] (deref fut)) futs-vec)))


(defn run-tnseq-phase-1 [] :NYI)

(defn run-tnseq-phase-2 [] (future :NYI))



(defn launch-action
  [eid recipient get-toolinfo template & {:keys [action rep compfile]}]
  (prn "LAUNCH: "
       [eid recipient template action rep compfile])
  (cond
    (#{"phase-0" "phase-0b" "phase-0c" "phase-1" "phase-2"} action)
    (let [phase action]
      (cond
        (#{"phase-0" "phase-0b" "phase-0c"} phase)
        (future
          (run-tnseq-phase-0 eid recipient get-toolinfo template))

        (#{"phase-1"} phase)
        (future
          (run-tnseq-phase-1 eid recipient get-toolinfo template :repk rep))

        (#{"phase-2"} phase)
        (run-tnseq-phase-2 eid recipient get-toolinfo template)))

    :else (str "TNSEQ: unknown action request " action)))


;;; (/ 1819874900 4)

;;(->> "TCTGAATCTCCTGCTACCGACCATGAACTAACAGGTTGGATGATAAGTCTT" pass-0-pipe count)

#_(let [bcs (cmn/get-exp-info eid :barcodes)
      sq "TCTGAATCTCCTGCTACCGACCATGAACTAACAGGTTGGATGATAAGTCTT"]
  (dotimes [_ 1000000]
    (let [sq (str/substring sq 9 -17)
          i (position-transpose-prefix sq "TAACAG")]
      (if (neg? i)
        -1
        (loop [bcs bcs]
          (if (seq bcs)
            (let [bc (first bcs)
                  tf (check-barcode sq bc)]
              (if tf
                [bc sq]
                (recur (rest bcs))))
            -1))))))




(comment

  (->> "TCTGAATCTCCTGCTACCGACCATGAACTAACAGGTTGGATGATAAGTCTT"
       pass-0-pipe count)

  (-> "GACTACCCTCTGGACCCCAACCGTCATATAAGATGTTGGATGATAAGTCCC"
      (trim-initsq-and-adapter 9 -17)
      (seq-less-transposon "TAACAG"))


  (def tnseqs
    ["TCTGAATCTCCTGCTACCGACCATGAACTAACAGGTTGGATGATAAGTCTT"
     "TCTGAATCTCCTGCTACCTGACCATGAACTAACAGGTTGGATGATAAGTCT"
     "TCTGAATCTCCTGCTACCTTGACCATGAACTAACAGGTTGGATGATAAGTC"
     "TCTGAATCTCCTGCTACCTTTGACCATGAACTAACAGGTTGGATGATAAGT"

     "TCTGAATCTCCTGCTACCGACCATGAACTAACxGGTTGGATGATAAGTCTT"
     "TCTGAATCTCCTGCTACCGACCATGAACTAxCxGGTTGGATGATAAGTCTT"
     "TCTGAATCTCCTGCTACCTTTGACCATGAACTxACAGGTTGGATGATAAGT"
     "TCTGAATCTCCTGCTACCTTTGACCATGAACTAACAGGTTGGATGATAAGT"
     ])

(let [sq "CCTGCTACCGACCATGAACTAACAG"
      tpn "TAACAG"
      mxtpn (count tpn)
      minsq (- (count sq) mxtpn)
      idxs0 (take (- mxtpn 2)
                  (iterate (fn[[x y]] [(inc x) (dec y)])
                           [minsq mxtpn]))
      maxsqi (->> idxs0 last first)]
  [mxtpn minsq idxs0 maxsqi])

(let [tnseqs ["CCTGCTACCGACCATGAACTAACAG"
              "CCTGCTACCGACCATGAACTAACxG"
              "CCTGCTACCGACCATGAACTAxCxG"
              "CCTGCTACCTTTGACCATGAACTxA"
              "CCTGCTACCTTTGACCATGAACTAA"]]
  (map #(position-transpose-prefix % "TAACAG") tnseqs))


(let [tnseqs ["CCTGCTACCGACCATGAACTAACAG"
              "CCTGCTACCGACCATGAACTAACxG"
              "CCTGCTACCGACCATGAACTAxCxG"
              "CCTGCTACCTTTGACCATGAACTxA"
              "CCTGCTACCTTTGACCATGAACTAA"]]
  (time (dotimes [_ 200000]
          (mapv #(position-transpose-prefix % "TAACAG") tnseqs))))


(let [tnseqs ["CCTGCTACCGACCATGAACTAACAG"
              "CCTGCTACCGACCATGAACTAACxG"
              "CCTGCTACCGACCATGAACTAxCxG"
              "CCTGCTACCTTTGACCATGAACTxA"
              "CCTGCTACCTTTGACCATGAACTAA"]]
  (->> tnseqs (map #(seq-less-transposon % "TAACAG"))))


(time (dotimes [_ 10000000]
        (->>  (str/sliding-take
               12 "TACTTACCTACTXCCGCTGGTCATCCTGCGCCAATTTGATGTGTGTGGTTTTTAATTG")
              (take 3)
              (map #(it/hamming "ACTTACCTACTT" %)))))

(time (dotimes [_ 1000000]
        (->>  (str/sliding-take
               6 "TACTTACCTACTXCCGCTGGTCATCCTGCGCCAATTTGATGTGTGTGGTTTTTAATTG")
              (take 3)
              (mapv #(it/hamming "ACTTAC" %)))))

(->>  (str/sliding-take
       6 "TACTTACCTACTXCCGCTGGTCATCCTGCGCCAATTTGATGTGTGTGGTTTTTAATTG")
      (take 3)
      (map #(it/hamming "ACTTAC" %))
      (apply min) (>= 1))

(->>  (str/sliding-take
       6 "TACTTACCTACTXCCGCTGGTCATCCTGCGCCAATTTGATGTGTGTGGTTTTTAATTG")
      (take 3)
      (map #(it/hamming "ACTTAC" %))
      (apply min) (>= 1))

)
