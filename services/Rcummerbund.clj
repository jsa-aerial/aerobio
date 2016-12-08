
{:name "Rcummerbund"
 :path ""
 :func  (fn [chartdir diffdir repstr data]
          (let [rep? (re-find #"Rep" chartdir)
                script (if rep? "cummerbundrep.r" "cummerbund3.r")
                script (fs/join (fs/pwd) "Scripts" script)]
            (infof "Running cummerbund on %s" diffdir)
            (pg/Rscript "--no-save" script
                        chartdir diffdir repstr)
            (str "cummerbund on " diffdir " -> " chartdir)))

 :description "Run cummerbund on differential data"
 }
