
{:name "directory-files"
 :path ""
 :repeat true
 :generator true
 :func (let [files (volatile! :nyi)]
         (fn[dir regex & _]
           (when (= @files :nyi)
             (vswap! files (fn[_] (sort (fs/re-directory-files dir regex)))))
           (let [fs @files]
             (if (seq fs)
               (let [f (first fs)]
                 (vswap! files (fn[_] (rest fs)))
                 f)
               (pg/done)))))

 :description "Streaming directory listing of DIR content matching REGEX string - not a live pattern, but the string represenation of a regex. Content is fully qualified and sorted. Sends one filespec at time until exhausted."
 }
