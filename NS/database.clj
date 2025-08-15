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
   [clojure.data.json :as json]

   [tech.v3.dataset :as ds]
   [tech.v3.datatype :as dtype]
   [tech.v3.datatype.functional :as df]
   [tech.v3.dataset.reductions :as dsr]
   [tablecloth.api :as tc]

   [next.jdbc :as jdbc]
   [next.jdbc.result-set :as rs]
   [tech.v3.dataset.sql :as dsql]
   
   [aerial.fs :as fs]
   [aerial.hanasu.common :as hc]
   [aerial.utils.coll :as coll]
   [aerial.utils.io :refer [letio] :as io]
   [aerial.bio.utils.files :as bufiles]

   [aerobio.tcutils]
   [aerobio.htseq.common :as cmn]])



(defmethod cmn/resultset->msgset "load-genbank"
  [result-maps]
  (let [[gbk-dir gbk done-dir err-dir] (->> result-maps first :value)
        msgs (for [resmap result-maps]
               (let [name (resmap :name)
                     [gbk-dir gbk done-dir err-dir] (resmap :value)
                     exit (resmap :exit)
                     err (resmap :err)
                     failed? (seq (filterv #(re-find #"failed" %) err))]
                 (if failed?
                   [exit err err-dir gbk]
                   [exit gbk])))]
    [gbk-dir done-dir msgs]))




;;; ------------------------------------------------------------------------;;;
;;;                        Connection Database                              ;;;
;;; ------------------------------------------------------------------------;;;


;;; Con DB storage
(defonce condb (atom {}))

(defn update-condb
  ([] (hc/update-db condb {}))
  ([keypath vorf]
   (hc/update-db condb keypath vorf))
  ([kp1 vof1 kp2 vof2 & kps-vs]
   (apply hc/update-db condb kp1 vof1 kp2 vof2 kps-vs)))

(defn get-condb
  ([] (hc/get-db condb []))
  ([key-path] (hc/get-db condb key-path)))


(defn connect-dbs []
  :mouse-db
  {:dbtype "mysql" :user "root" :password ""
   :dbname "GRCm38_mm10"}

  :refseq77-db
  {:dbtype "mysql" :user "root" :password ""
   :dbname "refseq77"}

  :tvoseq02-db
  {:dbtype "mysql" :user "root" :password ""
   :dbname "tvoseq02"}

  :exp-db
  {:dbtype "mysql" :user "root" :password ""
   :dbname "exp"}

  :r77con ;(-> refseq77-db jdbc/get-datasource jdbc/get-connection)
  :tvocon ;(-> tvoseq02-db jdbc/get-datasource jdbc/get-connection)
  :mmcon  ;(-> mouse-db jdbc/get-datasource jdbc/get-connection)
  :expcon ;(-> exp-db jdbc/get-datasource jdbc/get-connection)
  )








;;; for auto ns require to server
(let [ns (ns-name *ns*)]
  [ns "db"])
