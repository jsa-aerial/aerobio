{:scratch-base "/ExpOut"
 :fastq-dirname "Fastq"
 :refdir "/Refs"
 :nextseq-base "/NextSeq2"
 :nextseq-fqdir "Data/Intensities/BaseCalls"

 :ports
 {:server 7070
  :repl 4003}

 :logging
 {:dir "~/.aerobio"
  :file "main-log.txt"}

 :jobs
 {:dir "~/.aerobio/DBs"
  :file "job-db.clj"}

 :biodb-info
 {:genomes
  {:base "/GenomeSeqs"
   :refseq46 "RefSeq46"
   :refseq58 "RefSeq58"
   :refseq77 "RefSeq77"
   :tvoseq02 "TVOSeq02"
   :default :refseq77}

  :blast
  {:base "/BlastDBs"
   :refseq58 "RefSeq58/refseq58_microbial_genomic"
   :refseq77 "RefSeq77/Microbial/refseq77_microbial_complete"
   :refseq77-arch "RefSeq77/Archaea/refseq77_arhaea_complete"
   :refseq77-bact "RefSeq77/Bacteria/refseq77_bacteria_complete"
   :default :refseq77}}

 :email {:default      "the-default-acct@yourorg.orgtype"
         ;; All your other users
         ;; keyword version of user acct name as key,
         ;; string of full email address as value
         }

 :mailcfg {:smtphost "smtp.gmail.com"
           :sender   "donotreply.iobio@bc.edu"
           :user     "your-aerobio-email-acct@something.something"
           :pass     "the-pw-for-acct"}
}
