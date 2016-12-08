(ns binpack.binpacker
  (:require [taoensso.sente.interfaces :as interfaces]
            [binpack.core :as bpack]))

(deftype binpacker []
  interfaces/IPacker
  (interfaces/pack [_ x] (bpack/pack x))
  (interfaces/unpack [_ x] (bpack/unpack x :keywordize)))


(def binary-packer (->binpacker))
