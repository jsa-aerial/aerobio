
{:nodes
 {:load
  {:name "load-biosql-db",
   :type "func",
   :args []}
  :prn1 {:type "func",
         :name "prn"}},
 :root :load
 :edges {:load [:prn1]}
 :cli {:options
       [["-i" "--input-dir InputStaging" "Input Staging Directory"
         :missing "The Input Staging directory is required"]
        ["-g" "--glob GBKglob" "File regex glob for gbks"
         :default "*.gbk"]
        ["-d" "--database DBname" "Data Base name"
         :missing "The Data Base name is required"]
        ["-p" "--pw DBpw" "Database Password"
         :missing "The Database Password is required"]
        ["-D" "--done DONEDir" "Directory for success gbks"
         :missing "The Directory for success gbks is required"]
        ["-E" "--error ERRDir" "Directory for error gbks"
         :missing "The Directory for error gbks is required"]
        ["-u" "--user UserAccnt" "User account (zulip/email) for result msg"
         :missing "The user account is required"]]
       :order [:input-dir :glob :database :pw :done :error :user]}}
