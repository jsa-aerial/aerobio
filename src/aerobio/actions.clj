;;--------------------------------------------------------------------------;;
;;                                                                          ;;
;;                     A E R O B I O . A C T I O N S                        ;;
;;                                                                          ;;
;; Permission is hereby granted, free of charge, to any person obtaining    ;;
;; a copy of this software and associated documentation files (the          ;;
;; "Software"), to deal in the Software without restriction, including      ;;
;; without limitation the rights to use, copy, modify, merge, publish,      ;;
;; distribute, sublicense, and/or sell copies of the Software, and to       ;;
;; permit persons to whom the Software is furnished to do so, subject to    ;;
;; the following conditions:                                                ;;
;;                                                                          ;;
;; The above copyright notice and this permission notice shall be           ;;
;; included in all copies or substantial portions of the Software.          ;;
;;                                                                          ;;
;; THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,          ;;
;; EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF       ;;
;; MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND                    ;;
;; NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE   ;;
;; LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION   ;;
;; OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION    ;;
;; WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.          ;;
;;                                                                          ;;
;; Author: Jon Anthony                                                      ;;
;;                                                                          ;;
;;--------------------------------------------------------------------------;;
;;

(ns aerobio.actions
  "Streaming job server with full tree graphs, function nodes, superior
   error handling, logging, caching, etc.
  "

  {:author "Jon Anthony"}

  (:require
   [clojure.tools.cli :refer [parse-opts]]
   [clojure.string :as cljstr]

   ;; Our parameter db
   [aerobio.params :as pams]
   ;; HTSeq
   [aerobio.htseq.common :as cmn]
   [aerobio.htseq.rnaseq :as htrs]
   [aerobio.htseq.tnseq :as htts]
   ))




;;; V 2.7+ we no longer directly use OS accounts, for msg recipients.
;;; We now force experimenters to correctly use the 'experimenter'
;;; field in the experiment record of the Exp-SampleSheet.

;;; 2.8+ we need a dispatch on type of run - std HTS or generic
;;; JOB. There may be more types as we go on, but for now at least
;;; these two.
;;; (ns-unmap 'aerobio.actions 'get-msg-recipient)
(defmulti get-msg-recipient (fn[kind & args] kind))

(defmethod get-msg-recipient :hts
  [_ eid]
  (cmn/get-exp-info eid :experimenter))


(defn get-job-cli-args [cliargs job-template]
  (let [name (get-in job-template [:nodes (job-template :root) :name])]
    (if (or (not (job-template :cli))
            (not (get-in job-template [:cli :options]))
            (not (get-in job-template [:cli :order])))
      [[(format "No, or incorrect, Cli field in Job definition '%s'" name)]
       (format
        "%s\nrequires a :cli field {:options [[...]...[...]], :order [...]}"
        (with-out-str (clojure.pprint/pprint job-template)))]
      (let [cli (job-template :cli)
            options (cli :options)
            order (cli :order)
            usage (cli :usage (format "%s <options>" name))
            args (cli :args [])
            missing (format "Missing primary arg(s): %s" (print-str args))
            argmap (parse-opts cliargs options :in-order true)
            summary (format "%s\n%s" usage (argmap :summary))
            errors (argmap :errors)
            options (argmap :options)
            arguments (argmap :arguments)
            errors (if (not= (count args) arguments)
                     (cons missing errors)
                     errors)
            params (mapv (fn[a] (options a)) order)
            arglist (-> params (concat arguments) vec)]
        [errors summary arglist]))))




(defmulti
  ^{:doc "Dispatch to server REST action cmd. Among the various actions available are 'run', 'compare' and 'sample'. args is the set of cmd specific parameter values."
    :arglists '([cmd & args])}
  action
  (fn[cmd & args] (keyword cmd)))


(defmethod action :run
  [_ eid params get-toolinfo template]
  (let [status (atom {:done []})
        {:keys [user cmd phase modifier compfile]} params
        rep (if (= modifier "replicates") :rep nil)
        exp (cmn/get-exp-info eid :exp)
        user (get-msg-recipient :hts eid)]
    {:status status
     :fut  (cmn/launch-action
            eid user
            get-toolinfo template
            :action phase
            :rep rep
            :compfile compfile
            :status status)}))


(defmethod action :job
  [_ cliargs get-toolinfo template]
  (let [status (atom {:done []})
        [errors summary arglist] (get-job-cli-args cliargs template)]
    (if errors
      {:error
       (with-out-str
         (let [errmsg (->> errors
                           (mapv (fn[i msg]
                                   (format "%s. %s" (inc i) msg))
                                 (range))
                           (cljstr/join "\n"))]
           (println (format "Error(s):\n%s\n\nUsage:\n%s" errmsg summary))))}
      {:status status
       :fut (cmn/launch-job
             arglist get-toolinfo template status)})))


;;; phase-0b remove Fastqs, Out, Samples, Stats
;;; phase-0c remove Out, Samples, Stats
;;; phase-1  remove Out
;;; phase-2  Keep Out*Bams, remove all other Out*/
;;; phase-2b remove Aggrs <-- Only tnseq
;;;
#_(->> (cmn/exp-ids) (map #(cmn/get-exp-info % :out)))
#_(->> (cmn/exp-ids) (map #(cmn/get-exp-info % :aggrs)))
#_(->> (cmn/exp-ids) (map #(cmn/get-exp-info % :samples)))
#_(->> (cmn/exp-ids) (map #(cmn/get-exp-info % :stats)))
(defmethod action :rerun
  [_ eid params get-toolinfo template]
  (let [status (atom {:done []})
        {:keys [user cmd phase modifier compfile]} params
        rep (if (= modifier "replicates") :rep nil)
        outdir (cmn/get-exp-info eid :out)
        exp (cmn/get-exp-info eid :exp)
        user (get-msg-recipient :hts eid)]
    {:status status
     :fut (cmn/launch-action
           eid user
           get-toolinfo template
           :action phase
           :rep rep
           :compfile compfile
           :status status)}))


(defmethod action :compare
  [_ eid params get-toolinfo template]
  (let [status (atom {:done []})
        exp (cmn/get-exp-info eid :exp)
        {:keys [user cmd phase modifier compfile]} params
        user (get-msg-recipient :hts eid)]
    {:status status
     :fut (cmn/launch-action
           eid user
           get-toolinfo template
           :action (name cmd)
           :compfile compfile
           :status status)}))

(defmethod action :xcompare
  [_ eid params get-toolinfo template]
  (let [status (atom {:done []})
        exp (cmn/get-exp-info eid :exp)
        {:keys [user cmd phase modifier compfile]} params
        user (get-msg-recipient :hts eid)]
    {:status status
     :fut (cmn/launch-action
           eid user
           get-toolinfo template
           :action (name cmd)
           :compfile compfile
           :status status)}))


(defmethod action :aggregate
  [_ eid params get-toolinfo template]
  (let [status (atom {:done []})
        exp (cmn/get-exp-info eid :exp)
        {:keys [user cmd phase modifier compfile]} params
        user (get-msg-recipient :hts eid)]
    {:status status
     :fut (cmn/launch-action
           eid user
           get-toolinfo template
           :action (name cmd)
           :compfile compfile
           :status status)}))


(comment
  #_{:status status
     :fut (aerobio.pgmgraph/future+
           (doseq [s [5000 3000 3000 7000]]
             (swap! status #(assoc % :done (conj (% :done) (/ s 1000))))
             (aerial.utils.misc/sleep s))
           #_(/ 10 0)
           #_(assert (= 1 0) ">>Unknown tool 'foobar'>>")
           "The result %s" [user eid template phase rep compfile])}
)
