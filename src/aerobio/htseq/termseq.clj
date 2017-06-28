;;--------------------------------------------------------------------------;;
;;                                                                          ;;
;;               A E R O B I O . H T S E Q . R N A S E Q                    ;;
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

(ns aerobio.htseq.termseq
  [:require
   [clojure.data.csv :as csv]
   [clojure.string :as cljstr]

   [aerial.fs :as fs]

   [aerial.utils.coll :refer [vfold] :as coll]
   [aerial.utils.string :as str]
   [aerial.utils.io :refer [letio] :as io]
   [aerial.utils.math.probs-stats :as p]
   [aerial.utils.math.infoth :as it]

   [aerial.bio.utils.filters :as fil]

   [aerobio.params :as pams]
   [aerobio.pgmgraph :as pg]
   [aerobio.htseq.common :as cmn]
   [aerobio.htseq.rnaseq :as rsq]]
  )

(defmethod cmn/get-comparison-files :termseq
  [_ & args]
  (apply rsq/get-comparison-files- args))

(defn split-filter-fastqs
  [eid]
  (cmn/split-filter-fastqs eid identity))

(defmethod cmn/get-phase-1-args :termseq
  [_ & args]
  (apply cmn/get-phase-1-args :rnaseq args))


(defmethod cmn/run-comparison :termseq
  [_ eid recipient compfile get-toolinfo template]
  (assert false "Term-Seq comparision NYI!"))

(defmethod cmn/run-phase-2 :termseq
  [_ eid recipient get-toolinfo template]
  (cmn/run-comparison :termseq
   eid recipient "ComparisonSheet.csv" get-toolinfo template))

