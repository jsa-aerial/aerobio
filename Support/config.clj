{:scratch-base "/ExpOut"
 :fastq-dirname "Fastq"
 :refdir "/Refs"
 :nextseq-base "/ExpIn"
 :exp-base "/ExpIn"
 :nextseq-fqdir "Data/Intensities/BaseCalls"
 :illum-fqdir "Data/Intensities/BaseCalls"
 :elembio-fqdir "Data/Samples"

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


 :comcfg {:mode :zulip ; or :email or [:zulip :email]
          :email {:accnts {:default "the-default-acct@yourorg.orgtype"
                           ;; All your other users
                           ;; keyword version of user acct name as key,
                           ;; string of full email address as value
                           }

                  :mailcfg {:smtphost "smtp.gmail.com"
                            :sender   "donotreply.aerobio@yourorg.orgtype"
                            :user     "aerobio@yourorg.orgtype"
                            :pass     "the-pw-for-acct"}}

          :zulip {:apiurl "https://myorganization.zulipchat.com/api/v1/messages"
                  ;; :content-type "application/x-www-form-urlencoded"
                  :accnts {:default "default scientist accnt name"
                           ; 
                           }
                  :options {:form-params {:type "stream"
                                          :to "Job Results"
                                          :topic "" ; Topic for results
                                          :content "" ; Result msg info
                                          }
                            :basic-auth ["mybot@myorganization.zulipchat.com"
                                         "mybot's API key"]}}}
}
