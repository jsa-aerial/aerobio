;;--------------------------------------------------------------------------;;
;;                                                                          ;;
;;               A E R O B I O . H T S E Q . P A I R E D                    ;;
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

(ns aerobio.htseq.paired
  [:require
   [aerial.fs :as fs]
   [aerial.utils.string :as str]
   [aerial.utils.io :refer [letio] :as io]
   [aerial.bio.utils.files :as bufiles]

   [aerobio.pgmgraph :as pg]
   [aerobio.params :as pams]])




(defn rxy [rl]
  (let [rv (str/split #":" rl)
        [lane cell x] (->> rv (drop 3) (take 3))
        y (->> 6 rv (str/split #" ") first)]
    (str lane "-" cell "-" x "-" y)))

(defn getr1-xyset [R1fq]
  (letio [in (io/open-file R1fq :in)]
    (loop [fqrec (bufiles/read-fqrec in)
           r1xyset #{}]
      (if (nil? (fqrec 0))
        r1xyset
        (recur (bufiles/read-fqrec in)
               (conj r1xyset (rxy (fqrec 0))))))))

;; base "/MiSeq/190515_M03632_0028_000000000-B29YM"
;; datadir "Data/Intensities/BaseCalls"

(defn demuxR2 [R1fq R2-infq R2-otfq]
  (letio [r1xyset (getr1-xyset R1fq)
          in (io/open-file R2-infq :in)
          ot (io/open-file R2-otfq :out)]
    (loop [fqrec (bufiles/read-fqrec in)
           cnt 0]
      (if (nil? (fqrec 0))
        cnt
        (let [r2xy (rxy (fqrec 0))
              qcline (fqrec 3)]
          (if #_(and (r1xyset r2xy) (pass-qcscore qcline 14 0.97))
              (r1xyset r2xy)
            (do
              (bufiles/write-fqrec ot fqrec)
              (recur (bufiles/read-fqrec in) (inc cnt)))
            (recur (bufiles/read-fqrec in) cnt)))))))


(defn R2fq-name [R1fq]
  (let [dir (fs/dirname R1fq)]
    (->> R1fq
         fs/basename
         (str/replace-re #"-R1" "-R2")
         (fs/join dir))))


(defn demux-R2-samp [eid samp]
  (let [expoutdir (pams/get-params :scratch-base)
        sampbase (fs/join expoutdir eid "Samples")
        sampdir (fs/join sampbase samp)
        R1fqs (fs/glob (fs/join sampdir "*R1.fastq.gz"))
        fastqdir (fs/join expoutdir eid (pams/get-params :fastq-dirname))
        fpattern (re-pattern (str "^" samp "_*_R2_*.fastq.gz"))
        R2infqs (fs/re-directory-files fastqdir fpattern)]
    (doseq [R2-infq R2infqs]
      (doseq [R1fq R1fqs]
        (let [R2-otfq (R2fq-name R1fq)]
          (println (apply format "%s -> %s : %s"
                          (map fs/basename [R2-infq R1fq R2-otfq])))
          (demuxR2 R1fq R2-infq R2-otfq))))))


(defn demux-R2-samp-no-replicates [eid samp]
  (let [expoutdir (pams/get-params :scratch-base)
        sampbase (fs/join expoutdir eid "Samples")
        sampdir (fs/join sampbase samp)
        R1fqs (fs/glob (fs/join sampdir "*R1.fastq.gz"))
        fastqdir (fs/join expoutdir eid (pams/get-params :fastq-dirname))
        fpattern (re-pattern (str "^" samp "_*_R2_*.fastq.gz"))
        R2infq (first (fs/re-directory-files fastqdir fpattern))]
    (doseq [R2-otfq (mapv R2fq-name R1fqs)]
      (pg/ln "-s" R2infq R2-otfq))))








(comment
  (demux-R2-samp "190515_M03632_0028_000000000-B29YM" "RTSP7BC02")
  (demux-R2-samp "190515_M03632_0028_000000000-B29YM" "RTSP7BC03")
  (demux-R2-samp "190515_M03632_0028_000000000-B29YM" "RTSP7BC05")

  )



