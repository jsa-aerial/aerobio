
{
 :name "bcl2fastq",
 :path "",
 :func (fn [eid & data]
         (let [nextseq-base (pams/get-params :nextseq-base)
               expdir (fs/join nextseq-base eid)]
           (infof "BCL2FASTQ on Exp %s" eid)
           (pg/bcl2fastq "--no-lane-splitting" "--runfolder-dir" expdir)
           eid))
 ;; instructional data used in /help
 :description  "Function wrapping utility for converting Illumina basecall files to fastqs",
}
