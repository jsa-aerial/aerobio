{:scratch-base "/ExpOut"
 :fastq-dirname "Fastq"
 :refdir "/Refs"
 :exp-base "/ExpIn"
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
