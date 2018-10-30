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


(defn get-mail-recipient
  [user]
  (let [recipient (pams/get-params [:email (keyword user)])]
    (if recipient
      recipient
      (pams/get-params [:email :default]))))


(defmethod action :run
  [_ eid params get-toolinfo template]
  (let [{:keys [user cmd action rep compfile]} params
        rep (if rep :rep nil)
        exp (cmn/get-exp-info eid :exp)
        user (get-mail-recipient user)]
    (cmn/launch-action
     eid user
     get-toolinfo template
     :action action
     :rep rep
     :compfile compfile)))


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
  (let [{:keys [user cmd action rep compfile]} params
        rep (if rep :rep nil)
        outdir (cmn/get-exp-info eid :out)

        exp (cmn/get-exp-info eid :exp)
        user (get-mail-recipient user)]

    (cmn/launch-action
     eid user
     get-toolinfo template
     :action action
     :rep rep
     :compfile compfile)))


(defmethod action :compare
  [_ eid params get-toolinfo template]
  (let [exp (cmn/get-exp-info eid :exp)
        {:keys [user cmd action rep compfile]} params
        recipient (pams/get-params [:email (keyword user)])]
    (cmn/run-comparison
     exp eid recipient compfile get-toolinfo template)))

(defmethod action :xcompare
  [_ eid params get-toolinfo template]
  (let [exp (cmn/get-exp-info eid :exp)
        {:keys [user cmd action rep compfile]} params
        user (get-mail-recipient user)]
    (cmn/run-comparison
     exp eid user compfile get-toolinfo template)))


(defmethod action :aggregate
  [_ eid params get-toolinfo template]
  (let [exp (cmn/get-exp-info eid :exp)
        {:keys [user cmd action rep compfile]} params
        recipient (pams/get-params [:email (keyword user)])]
    (htts/run-aggregation
     eid recipient compfile get-toolinfo template)))
