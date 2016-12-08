(ns iobio.htseq.wgseq
  [:require
   [clojure.string :as cljstr]

   [aerial.fs :as fs]
   [aerial.utils.string :as str]
   [aerial.utils.coll :refer [vfold] :as coll]
   [aerial.utils.string :as str]
   [aerial.utils.io :refer [letio] :as io]

   [aerial.bio.utils.files :as bufiles]
   [aerial.bio.utils.filters :as fil]

   [iobio.params :as pams]
   [iobio.htseq.common :as cmn]
   [iobio.pgmgraph :as pg]])


(defn filter-fastq
  [fastq & {:keys [baseqc% winsize info%  min-len sqc% marker pretrim outdir]
            :or {baseqc% 0.95, winsize 10, info% 0.9
                 min-len 40, sqc% 0.97 pretrim 11}}]
  (letio [[qc-ctpt ent-ctpt] (fil/qcscore-min-entropy baseqc% info% winsize)
          totcnt (volatile! 0)
          gdcnt (volatile! 0)
          lens (volatile! {})
          rec-chunk-size 10000

          f (fs/fullpath fastq)
          outdir (if outdir outdir (fs/dirname f))
          statf (->> f fs/basename (str/split #"\.") first
                     (#(str % "-qc" qc-ctpt "-stat.txt"))
                     (fs/join outdir))

          otfq (->> f fs/basename (str/split #"\.") first
                    (#(str % "-qc" qc-ctpt ".fastq.gz"))
                    (fs/join outdir))
          ot (io/open-streaming-gzip otfq :out)

          inf (if (not= (fs/ftype f) "gz")
                (clojure.java.io/reader f)
                (io/open-streaming-gzip f :in))]
    (loop [recs (bufiles/read-fqrecs inf rec-chunk-size)]
      (if (not (seq recs))
        (io/with-out-writer statf
          (prn {:run-params {:sqc% (* 100 sqc%)
                             :base-quality (* 100 baseqc%)
                             :window-size winsize
                             :info% (* 100 info%)
                             :min-len min-len}
                :good-cnt @gdcnt
                :total-cnt @totcnt
                :bad-cnt (- @totcnt @gdcnt)
                :percent-good (* 100 (double (/ @gdcnt @totcnt)))
                :len-dist @lens}))
        (let [xxx (vfold #(fil/seq-filter % :qc-ctpt qc-ctpt
                                          :winsize winsize
                                          :ent-ctpt ent-ctpt)
                         recs)
              xxx (vfold #(fil/trim-ends % pretrim min-len sqc% marker) xxx)]
          (doseq [[gcnt id gsq gqc qc%] xxx]
            (vswap! lens #(assoc % gcnt (inc (long (get % gcnt 0)))))
            (vswap! totcnt #(inc (long %)))
            (when (and (> gcnt min-len) (>= qc% sqc%))
              (vswap! gdcnt #(inc (long %)))
              (bufiles/write-fqrec-to-file
               ot [(str id "\n")
                   (str gsq "\n")
                   (str "+ " gcnt " " qc% "\n")
                   (str gqc "\n")])))
          (recur (bufiles/read-fqrecs inf rec-chunk-size)))))))





(comment

  (fs/dodir "/data1/NextSeq/TVOLab/KarenWGS012816/Fastq/Raw/"
            #(fs/re-directory-files % "Day4N2*.fastq.gz")
            #(filter-fastq
              % :baseqc% 0.96 :sqc% 0.97 :marker "CTGTCTC"
              :outdir "/data1/NextSeq/TVOLab/KarenWGS012816/Fastq/Filtered"))

  (fs/dodir "/data1/NextSeq/TVOLab/KarenWGS012816/Fastq/Raw/"
            #(fs/re-directory-files % "Day10*.gz")
            #(filter-fastq
              % :baseqc% 0.96 :sqc% 0.97 :marker "CTGTCTC"
              :outdir "/data1/NextSeq/TVOLab/KarenWGS012816/Fastq/Filtered"))

  (fs/dodir "/data1/NextSeq/TVOLab/KarenWGS012816/Refs"
            #(fs/re-directory-files % "wt*.fastq.gz")
            #(filter-fastq
              % :baseqc% 0.96 :sqc% 0.97 :marker "CTGTCTC"
              :outdir "/data1/NextSeq/TVOLab/KarenWGS012816/Fastq/Filtered"))

  (fs/dodir "/data1/NextSeq/TVOLab/KarenWGS012816/Fastq/Raw/"
            #(fs/re-directory-files % "wt*.fastq.gz")
            #(filter-fastq
              % :baseqc% 0.96 :sqc% 0.97 :marker "CTGTCTC"
              :outdir "/data1/NextSeq/TVOLab/KarenWGS012816/Fastq/Filtered"))
)
