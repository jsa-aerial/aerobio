;;--------------------------------------------------------------------------;;
;;                                                                          ;;
;;                A E R O B I O . H T S E Q . T N S E Q                     ;;
;;                                                                          ;;
;; Permission is hereby granted, free of charge, to any person obtaining    ;;
;; a copy of this software and associated documentation files (the          ;;
;; "Software"), to deal in the Software without restriction, including      ;;
;; without limitation the rights to use, copy, modify, merge, publish,      ;;
;; distribute, sublicense, and/or sell copies of the Software, and to       ;;
;; permit persons to whom the Software is furnished to do so, subject to    ;;
;; the following conditions:                                                ;;
;;                                                                          ;;
;; The above copyright notice and this permission notice shall be           ;;
;; included in all copies or substantial portions of the Software.          ;;
;;                                                                          ;;
;; THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,          ;;
;; EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF       ;;
;; MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND                    ;;
;; NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE   ;;
;; LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION   ;;
;; OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION    ;;
;; WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.          ;;
;;                                                                          ;;
;; Author: Jon Anthony                                                      ;;
;;                                                                          ;;
;;--------------------------------------------------------------------------;;
;;

(ns iobio.htseq.tnseq
  [:require
   [clojure.string :as cljstr]

   [aerial.fs :as fs]
   [aerial.utils.string :as str]
   [aerial.utils.coll :as coll]
   [aerial.utils.io :refer [letio] :as io]
   [aerial.utils.math.infoth :as it]
   [aerial.bio.utils.files :as bufiles]

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


(defn get-collapse-groups
  [eid]
  (let [base (cmn/get-exp-info eid :base)
        exp-illumina-xref (cmn/get-exp-info eid :exp-illumina-xref)
        illumina-sample-xref (cmn/get-exp-info eid :illumina-sample-xref)
        ifqs (cmn/get-bc-file-specs base exp-illumina-xref
                                    illumina-sample-xref)
        collapse-specs (cmn/get-bc-file-specs base exp-illumina-xref
                                              illumina-sample-xref
                                              :ftype ".collapse.fna.gz")
        groups (map (fn[ibc]
                      [ibc (reduce (fn[V [ebc fq]]
                                     (conj V [fq (get-in collapse-specs
                                                         [ibc ebc])]))
                                   [] (ifqs ibc))])
                    (keys ifqs))]
    (mapv (fn [[ibc pairs]] pairs) groups)))

(defn write-farec
  [^java.io.BufferedWriter ot rec]
  (let [[id sq] rec]
    (.write ot (str id "\n"))
    (.write ot (str sq "\n"))))

(defn collapse-one [[fastq fasta]]
  (letio [inf (io/open-streaming-gzip fastq :in)
          otf (io/open-streaming-gzip fasta :out)
          cnt (loop [fqrec (bufiles/read-fqrec inf)
                     M {}]
                (if (nil? (fqrec 0))
                  (reduce (fn[C [sq cnt]]
                            (let [id (str ">" C "-" cnt)]
                              (write-farec otf [id sq])
                              (inc C)))
                          1 M)
                  (let [sq (fqrec 1)]
                    (recur (bufiles/read-fqrec inf)
                           (assoc M sq (inc (get M sq 0)))))))]
      {:name "collapser"
       :value [fasta cnt]
       :exit :success
       :err ""}))

(defn run-collapse-group
  [pairs]
  (let [futs (mapv (fn[pair] (future (collapse-one pair))) pairs)]
    (mapv (fn[fut] (deref fut)) futs)))


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



;;; Get primary phase 1 arguments. These are the bowtie index, the
;;; fastq set, the collapsed fastas, output map file, output bam and
;;; bai file names
(defmethod cmn/get-phase-1-args :tnseq
  [_ eid repname & {:keys [repk]}]
  (let [fqs (cljstr/join "," (cmn/get-replicate-fqzs eid repname repk))
        fnas (cljstr/join
              "," (mapv (fn[fq]
                          (->> fq (str/split #"\.")
                               (coll/takev-until #(= % "fastq"))
                               (cljstr/join ".")
                               (#(str % ".collapse.fna.gz"))))
                        (str/split #"," fqs))) _ (prn :FQS fqs :FNAS fnas)
        refnm (cmn/replicate-name->strain-name eid repname)
        btindex (fs/join (cmn/get-exp-info eid :index) refnm)
        otbam (fs/join (cmn/get-exp-info eid repk :bams) (str repname ".bam"))
        otmap (fs/join (cmn/get-exp-info eid repk :maps) (str repname ".map"))
        otbai (str otbam ".bai")
        refgtf (fs/join (cmn/get-exp-info eid :refs)
                        (str refnm ".gtf"))]
    (apply cmn/ensure-dirs (map fs/dirname [otbam otbai otmap]))
    [btindex fqs fnas otmap otbam otbai]))


(defmethod cmn/run-phase-2 :tnseq
  [_ eid recipient get-toolinfo template]
  (assert false "TNSEQ PHASE 2 NYI"))



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
