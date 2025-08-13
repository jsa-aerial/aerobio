;;--------------------------------------------------------------------------;;
;;                                                                          ;;
;;                   A E R O B I O . D A T A B A S E                        ;;
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

(ns aerobio.database
  [:require
   [clojure.string :as str]
   [clojure.set :as set]

   [aerial.fs :as fs]
   [aerial.utils.coll :as coll]
   [aerial.utils.io :refer [letio] :as io]
   [aerial.bio.utils.files :as bufiles]

   [aerobio.htseq.common :as cmn]])



(defmethod cmn/resultset->msgset "load-genbank"
  [result-maps]
  (let [[gbk-dir gbk done-dir err-dir] (->> result-maps first :value)
        msgs (for [resmap result-maps]
               (let [name (resmap :name)
                     [gbk-dir gbk done-dir err-dir] (resmap :value)
                     exit (resmap :exit)
                     err (resmap :err)]
                 (if (= exit :success)
                   [exit gbk]
                   [exit err err-dir gbk])))]
    [gbk-dir done-dir msgs]))

