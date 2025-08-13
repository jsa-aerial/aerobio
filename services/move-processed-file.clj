
{
 :name "move-processed-file",
 :path "",
 :func (fn [done-dir err-dir retmap]
         (if (pg/eoi? retmap)
           (pg/done)
           (let [name (retmap :name)
                 file (retmap :value)
                 basename (fs/basename file)
                 exit (retmap :exit)
                 err (retmap :err)
                 resmap {:name name
                         :value [(fs/dirname file) basename
                                 done-dir err-dir]
                         :exit exit
                         :err err}]
             (if (seq (filterv #(re-find #"WARN" %) err))
               (warnf "%s: [%s] %s, %s" name exit basename err)
               (infof "%s: [%s] %s" name exit basename))
             (if (= exit :success)
               (fs/move [file] done-dir)
               (fs/move [file] err-dir))
             resmap)))

 :description "Move the file given in retmap to either done-dir or err-dir depending on status of upstream processing. retmap is {:name nnn :value vvv :exit eee :err sss} where nnn is name of upstream processor, vvv is the full file spec, eee is either :success or exit code of processor, and sss is a seq of strings (possibly empty) describing error - or warnings."
}
