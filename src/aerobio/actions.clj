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

   ;; Our parameter db
   [aerobio.params :as pams]
   ;; HTSeq
   [aerobio.htseq.common :as cmn]
   [aerobio.htseq.rnaseq :as htrs]
   [aerobio.htseq.tnseq :as htts]
   ))



(defmulti
  ^{:doc "Dispatch to server REST action cmd. Among the various actions available are 'run', 'compare' and 'sample'. args is the set of cmd specific parameter values."
    :arglists '([cmd & args])}
  action
  (fn[cmd & args] (keyword cmd)))



;;; V 2.7+ we no longer directly use OS accounts, for msg recipients.
;;; We now force experimenters to correctly use the 'experimenter'
;;; field in the experiment record of the Exp-SampleSheet.
(defn get-msg-recipient
  [eid]
  (cmn/get-exp-info eid :experimenter))


(defmethod action :run
  [_ eid params get-toolinfo template]
  (let [status (atom {:done []})
        {:keys [user cmd phase modifier compfile]} params
        rep (if (= modifier "replicates") :rep nil)
        exp (cmn/get-exp-info eid :exp)
        user (get-msg-recipient eid)]
    {:status status
     :fut  (cmn/launch-action
            eid user
            get-toolinfo template
            :action phase
            :rep rep
            :compfile compfile
            :status status)}))


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
        user (get-msg-recipient eid)]
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
        user (get-msg-recipient eid)]
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
        user (get-msg-recipient eid)]
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
        user (get-msg-recipient eid)]
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
