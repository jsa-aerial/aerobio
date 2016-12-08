{
 :name "Two bam streaming merger",
 :path  "",

 :graph {:i1 {:type :url}
         :i2 {:type :url}
         :s1 {:type :tool :path "samtools"
              :args ["view" "-b" "%1" "#1"]}
         :s2 {:type :tool :path "samtools"
              :args ["view" "-b" "%1" "#1"]}
         :bt {:type :tool :path "bamtools"
              :inflag "-in"
              :args []}
         :edges {:s1 [:bt], :s2 [:bt], :i1 [:s1], :i2 [:s2]}}

 ;; instructional data used in /help
 :description "download and merge a region of multiple bam files",
 :exampleUrl "http://bamMerger.aerobio?cmd=11:10108473-10188473%20'http://s3.amazonaws.com/1000genomes/data/NA06984/alignment/NA06984.chrom11.ILLUMINA.bwa.CEU.low_coverage.20111114.bam'%20'http://s3.amazonaws.com/1000genomes/data/NA06985/alignment/NA06985.chrom11.ILLUMINA.bwa.CEU.low_coverage.20111114.bam'"
}
