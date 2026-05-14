;;--------------------------------------------------------------------------;;
;;                                                                          ;;
;;                   A E R O B I O . U N C O M P                            ;;
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

(ns aerobio.uncomp

  (:require
   [uncomplicate.commons.core
    :refer [with-release let-release
            Releaseable release]]
   [uncomplicate.fluokitten.core
    :refer [fmap fmap!]]
   [uncomplicate.neanderthal
    [auxil
     :refer [sort+! sort-!]]
    [block
     :refer [buffer contiguous?]]
    [native
     :refer [fv dv dge fge fgd ftr dtr native-float]]
    [core
     :as nc
     :refer [copy copy! mv! mv axpy! scal! amax
             submatrix subvector
             transfer! transfer
             dim mrows ncols dot nrm2 mm mm! mmt
             rows cols rk rk! axpy axpy!
             row asum sum trans dia
             view-ge view-tr view-vctr]]
    [real
     :refer [entry entry!]]
    [math
     :as nm
     :refer [signum exp sqrt sin asin cos acos]]
    [vect-math
     :as vm
     :refer [fmax! tanh! mul mul! div div! round
             linear-frac! sqrt! inv!]]
    [random
     :as nrnd
     :refer [rand-normal! rand-uniform! rng-state]]
    [linalg
     :as nla
     :refer [trf tri det]]]
   [uncomplicate.diamond.internal.dnnl.factory :as dnnlfact]
   [uncomplicate.diamond.tensor :as dt]
   [uncomplicate.diamond.native :as dnat]))


(alter-var-root #'uncomplicate.diamond.tensor/*diamond-factory*
                (constantly (dnnlfact/dnnl-factory)))


(with-release
  [a (dge 2 3 [1 2 3 4 5 6])
   b (dge 3 2 [1 3 5 7 9 11])]
  (mm a b))

