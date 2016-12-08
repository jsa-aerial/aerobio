{
 :name "cuffmerge",
 :path "cuffmerge",
 ;; instructional data used in /help
 :description
 "utility to merge merge together several Cufflinks assemblies, making it easier to produce an assembly GTF file suitable for use with cuffdiff.  cuffmerge also runs cuffcompare in the background to filter out transcribed fragments (transfrags) that are likely to be artifacts.  Essentially a \"meta-assembler\": it treats the assembled transfrags from Cufflinks the way that Cufflinks treats reads, by merging them together parsimoniously, producing the smallest number of transcripts that explain the data."
}
