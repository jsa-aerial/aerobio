{:name "cuffmerge-cuffdiff"
 :path ""
 :func  (fn [merge-dir asm-txt refgtf
            cuffdiff reffna repstr merge-gtf bamfiles]
          (infof "Running cuffmerge on %s" asm-txt)
          (pg/cuffmerge "-g" refgtf "-o" merge-dir asm-txt)
          (aum/sleep 1000)
          (pg/cuffcompare "-r" refgtf "-Q" "-s" reffna
                          "-o" (fs/join merge-dir "cmp") merge-gtf)
          (aum/sleep 1000)
          (infof "Running cuffdiff on %s" merge-gtf)
          (apply pg/cuffdiff
                 "-p" "16" "-o" cuffdiff
                 "-b" reffna "-L" repstr "-u"
                 (fs/join merge-dir "cmp.combined.gtf")
                 bamfiles)
          (infof "cuffmerge -> %s | cuffdiff -> %s" merge-dir cuffdiff))

 :description "cuffmerge and cuffdiff are not streamable, so we need to hack a synchronous function which calls cuffmerge first, then cuffdiff."
 }
