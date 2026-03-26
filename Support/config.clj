{:scratch-base "/ExpOut"
 :fastq-dirname "Fastq"
 :exp-base "/ExpIn"
 :illum-fqdir "Data/Intensities/BaseCalls"
 :elembio-fqdir "Data/Samples"

 :ports
 {:server 7070
  :repl 4003}

 :logging
 {:console? false
  :level :info
  :timezone :jvm-default
  :file "main-log.txt"}

 :jobs
 {:dir "DBs"
  :file "job-db.clj"}


 :refdata
 {;; These defaults would have the root dirs in Aerobio home/install
  ;; directory. Use full paths (starting with OS filesys separator '/'
  ;; for POSIX, '\' for Win) to place directories elsewhere
  :gbks "GBKs" ; root directory containing genbank files
  :fastas "Fastas" ; root directory to contain genome fasta files
  :refdir "Refs" ; root directory for reference data
  ;; WILL BE SUB directories of :refdir!!
  :bt1idx "BT1Index"
  :bt2idx "BT2Index"
  :staridx "STARIndex"
  :normgenes "NormGenes"}

 ;; Bio DBs configs
 ;; Default to not having these
 ;; only used in special modules
 :have-biodbs false
 :biodb-info
 {:genomes
  {:base "basedir-for-genomes"
   ;; version directory pairs
   :default :default-version-from-pairs}

  :blast
  {:base "basedir-for-blastdbs"
   ;; version directory pairs
   :default :default-version-from-pairs}}


 :comcfg {:mode :none ; or :zulip or :email or [:zulip :email]
          :email {:accnts {:default "the-default-acct@yourorg.orgtype"
                           ;; All your other users
                           ;; keyword version of user acct name as key,
                           ;; string of full email address as value
                           }

                  :mailcfg {:smtphost "smtp.yourorg.com" ; eg "smtp.gmail.com"
                            :sender   "donotreply.aerobio@yourorg.orgtype"
                            :user     "aerobio@yourorg.orgtype"
                            :pass     "the-pw-for-acct"}}

          :zulip {:apiurl "https://myorganization.zulipchat.com/api/v1/messages"
                  ;; :content-type "application/x-www-form-urlencoded"
                  :accnts {:default "default scientist accnt name"
                           ;; All your other users
                           ;; Keys are names and nicknames of users
                           ;; Values are full Zulip account name
                           }
                  :options {:form-params {:type "stream"
                                          :to "Job Results"
                                          :topic "" ; Topic for results
                                          :content "" ; Result msg info
                                          }
                            :basic-auth ["mybot@myorganization.zulipchat.com"
                                         "mybot's API key"]}}}
}
