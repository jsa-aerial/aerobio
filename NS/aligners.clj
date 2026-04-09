
(in-ns 'aerobio.pgmgraph)


;;; (ns-unmap 'aerobio.htseq.common 'align)
(defmulti align
  "Call the appropriate aligner as given by phase1 args (or default)"
  {:arglists '([aligner args])}
  (fn [aligner args]
    (infof "Align MM %s %s" aligner args)
    (keyword aligner)))

(defmethod align :bowtie
  [_ args]
  (apply shl/proc "bowtie" args))

(defmethod align :bowtie2
  [_ args]
  (apply shl/proc "bowtie2" args))

(defmethod align :STAR
  [_ args]
  (apply shl/proc "STAR" args))

(defmethod align :default
  [aligner args]
  (infof "No such registered aligner [%s], args '%s'" aligner args))




;;; for auto ns require to server
(let [ns (ns-name *ns*)]
  [ns "pg"])
