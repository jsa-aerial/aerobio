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
  (let [i (position-transpose-prefix sq tpn)]
    (when (pos? i)
      (str/substring sq 0 (+ 2 i)))))

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


(defn pass-0-pipe [sq]
  (-> sq (trim-initsq-and-adapter 9 -17)
      (seq-less-transposon "TAACAG")
      (trim-barcode 6)))

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
