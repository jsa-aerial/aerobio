;;--------------------------------------------------------------------------;;
;;                                                                          ;;
;;                   A E R O B I O . T C U T I L S                          ;;
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

(ns aerobio.tcutils
  [:require

   [clojure.string :as str]

   [aerial.fs :as fs]

   [tech.v3.dataset :as ds]
   [tech.v3.datatype :as dtype]
   [tech.v3.datatype.functional :as df]
   [tech.v3.dataset.reductions :as dsr]
   [tech.v3.dataset.rolling :as drl]
   [tech.v3.dataset.column :as dsc]

   [tablecloth.api.utils :as tcu]
   [tablecloth.api :as tc]
   [tablecloth.column.api :as tcc]
   ])




(defn write-dataset [ds dir & {:keys [ftype gz?] :or {ftype "csv" gz? true}}]
  (let [dsname (tc/dataset-name ds)
        fspec (fs/join dir (format "%s.%s" dsname ftype))
        fspec (fs/fullpath (if gz? (format "%s.%s" fspec "gz") fspec))]
    (when (not (fs/exists? dir)) (fs/mkdirs dir))
    (tc/write! ds fspec)
    ds))


(defn desc-stats [ds]
  (let [ds (tc/info ds)]
    (-> ds
      (tc/add-or-replace-columns
       (let [sd (ds :standard-deviation)
             vr (dtype/make-reader
                 :float64 (tc/row-count ds)
                 (let [x (sd idx)]
                   (if (number? x) (* x x) -1)))]
         {:variance vr}))
      (tc/rename-columns {:standard-deviation :std-dev})
      (tc/select-columns
       [:col-name :min :max :mean :std-dev :variance :skew])
      (tc/reorder-columns [:col-name :min :max] [:mean :std-dev :variance]))))


(defn colfreq [ds col cntcol]
  (-> ds
    (tc/group-by [col])
    (tc/aggregate {cntcol tc/row-count})))


(require '[tablecloth.api.utils :as tcu])

(defn reduce-cols [keycol col-reducer-map ds-seq]
  (if (and (tc/dataset? ds-seq) (tc/grouped? ds-seq))
    (tcu/process-group-data
     ds-seq
     (partial reduce-cols keycol col-reducer-map))
    (let [ds-seq (if (tc/dataset? ds-seq) (vector ds-seq) ds-seq)
          colnames (-> ds-seq first tc/column-names)
          dsname (->> ds-seq (mapv tc/dataset-name) (str/join "-"))
          keynames (if (seq? keycol)
                     (->> keycol
                       (mapv #(if (keyword? %) (name %) (str %)))
                       (str/join "-"))
                     (if (keyword? keycol) (name keycol) (str keycol)))]
      (->
        (dsr/group-by-column-agg
         keycol
         (-> colnames
           (->> (mapv #(vector % (dsr/first-value %))) (into (array-map)))
           (merge col-reducer-map))
         ds-seq)
        (tc/set-dataset-name (format "%s-%s-%s" dsname keynames "reduced"))))))


