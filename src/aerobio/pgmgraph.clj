;;--------------------------------------------------------------------------;;
;;                                                                          ;;
;;                    A E R O B I O . P G M G R A P H                       ;;
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

(ns aerobio.pgmgraph

  (:require
   [clojure.java.io :as io :refer [file output-stream input-stream]]
   [clojure.core.async :as async :refer [go go-loop >! <! >!! <!!]]
   [clojure.set        :as set]
   [clojure.string     :as str]

   [taoensso.timbre    :as timbre
    :refer (tracef debugf infof warnf errorf)]

   [me.raynes.conch :refer [programs with-programs let-programs] :as sh]
   [me.raynes.conch.low-level :as shl]

   [postal.core :as postal]

   ;; For pgm graph data shape validation
   [schema.core :as sch]

   [aerial.utils.coll :as coll :refer [in takev-until dropv-until ensure-vec]]
   [aerial.fs :as fs]

   [aerobio.params :as pams])

  (:import
   [java.io InputStream OutputStream]
   [java.nio ByteBuffer]
   [java.util Arrays]))

;;(sch/validate sch/Str "1")



(defmacro future+ [& body]
  `(future
     (try
       ~@body
       (catch Error e#
         {:error (type e#), :msg ((Throwable->map e#) :cause)})
       (catch Exception e#
         {:error (type e#), :msg ((Throwable->map e#) :cause)}))))


(defn init-pgms
  "Setup external 'canned' programs as standard functions. This makes
  user function nodes much simpler in many cases."
  []
  (programs bcl2fastq samtools
            bowtie bowtie2 bowtie-build bowtie2-build
            featureCounts breseq
            Rscript python ruby perl
            ln
            bp_load_genbank
            ))

(defn send-msg [recipients subject body]
  (let [{:keys [smtphost sender user pass]} (pams/get-params :mailcfg)]
    (postal/send-message
     {:host smtphost
      :user user
      :pass pass
      ;;:tls true
      ;;:port 587
      :ssl true}
     {:from sender
      :to recipients
      :subject subject
      :body body})))



(defn get-pipe [name]
  (let [wd (fs/pwd)
        pipe-path (fs/join wd "pipes" (str name ".p"))]
    (with-programs [mkfifo]
      (mkfifo pipe-path))
    pipe-path))

(defn rm-pipe [path]
  (fs/delete path))


(defn read-stream
  [^InputStream instrm, #^bytes buf]
  (let [n (.read instrm buf)]
    (if (neg? n)
      nil
      (Arrays/copyOfRange buf 0 n))))

(defn write-stream
  [^OutputStream otstrm, data]
  (.write otstrm data))

(defn close-stream
  [strm]
  (.close strm))


(let [batype (type (byte-array []))]
  (defn byte-array? [x]
    (instance? batype x)))

(defn proc? [x]
  (and (map? x) (:process x)))

(defn stream? [x]
  (and (map? x) (:setfields x)))

(defn jstream? [x]
  (or (isa? (class x) InputStream)
      (isa? (class x) OutputStream)))


(defn url? [x]
  (and (map? x) (:url x)))

(defn bsurl? [x]
  (and (url? x) (:bsc x)))

(defn tool? [x]
  (and (map? x) (:cmd x)))

(defn func? [x]
  (and (map? x) (:func x)
       (fn? (:func x))))

(defn destroy-procs [procs]
  (map shl/destroy (filter proc? procs)))

(defn close-streams [items]
  ;;example.bjsapp/testin
  (map #(.close %) (filter jstream? items)))




;;; --------------------------------------------------------------------------
;;; Node pgm graph - named service defines sub program graph

(defn top-sort-nodes [edges ins starts] #_(prn edges ins starts)
  (loop [L [], S starts
         E edges, I ins] #_(prn :L L :S S :E E :I I)
    (if (empty? S)
      [L E]
      (let [n (first S)
            ms (E n)
            nxtE (dissoc E n)
            nxtI (reduce (fn[I m] (assoc I m (remove #(= % n) (I m)))) I ms)]
        (recur (conj L n)
               (reduce (fn[S m] (if (empty? (nxtI m)) (conj S m) S))
                       (disj S n) ms)
               nxtE, nxtI)))))

(defn edges->ins [edges]
  (reduce (fn[Ins [i ovec]]
            (reduce (fn[Ins o]
                      (assoc Ins o (conj (get Ins o []) i)))
                    Ins ovec))
          {} edges))

(defn node-graph [graph get-toolinfo inputs args]
  #_(prn :GRAPH graph :INPUTS inputs :ARGS args)
  (let [nodes (->> graph (filter (fn[[k v]] (v :type))) (into {}))
        tools (->> nodes
                   (filter (fn[[k v]]
                             (#{:tool "tool" "func" "stream"} (v :type))))
                   (into {}))
        tools (->> tools
                   (map (fn[[k v]]
                          (let [{:keys [graph path func src repeat]}
                                (get-toolinfo (or (v :name) (v :path)))]
                            [k (merge {:inpipes [], :outpipes []
                                       :id (gensym (or (v :name) (v :path)))
                                       :func func :src src :repeat repeat
                                       :tool (v :path)} v)])))
                   (into {}))
        edges (or (graph :edges) {})
        ins (edges->ins edges)
        starts (set/difference (-> nodes keys set) (-> ins keys set))
        ;;_ (prn :Es edges :Is ins :Ss starts)
        [lingraph leftover] (if edges
                              (top-sort-nodes edges ins starts)
                              [[(first starts)] nil])
        _ (assert (empty? leftover) (str "Graph " graph " has cycle(s)!"))]
    #_(prn :NODES nodes :TOOLS tools)
    [lingraph edges ins args
     (merge tools
            (reduce (fn[M m] (assoc M (m :id) m)) {} inputs)
            (->> (set/difference (-> nodes keys set) (-> tools keys set))
                 (reduce (fn [M k]
                           (let [i (-> k name (subs 1) Integer. dec)
                                 n (nth inputs i)
                                 n (if (#{"url" "bsurl"} (n :type))
                                     (assoc n :id (get n :id (gensym "http")))
                                     n)]
                             (assoc M k n)))
                         {})))]))


(defn add-input-pipes [node invec nodes]
  #_(prn :AIP :ND node :INVEC invec :NDS nodes)
  (let [pipes (->> invec (mapv #((nodes %) :pipe))(filterv seq))]
    (if (or (empty? pipes)
            (seq (node :inpipes)))
      node
      (let [inflag (node :inflag)
            pipeargs (if inflag
                       (interleave (repeat inflag) pipes)
                       pipes)
            args (node :args)
            nargs (concat (takev-until #(= "#p" %) args)
                         pipeargs
                         (->> args (dropv-until #(= "#p" %)) rest))]
        (assoc node :args (vec nargs) :inpipes (vec pipes))))))

(defn add-output-pipes [n node edges ins]
  #_(prn :AOP n node edges ins)
  (cond
   (node :pipe) node,
   (not (every? #(<= % 1) (->> n edges (map #(count (ins %))))))
   (assoc node :pipe (get-pipe (node :id))),
   :else node))

(defn replacement-map [kvfn type-ch coll]
  (into {} (mapv kvfn
                 (map #(str type-ch %) (range 1 90))
                 coll)))

(defn inputs-as-args? [node]
  (or (node :inputs-as-args)
      (and (node :args)
           (every? string? (node :args))
           (some #(re-find #"^%[0-9]+" %) (node :args)))))

(defn get-unrolled-node-map [lingraph edges ins args nodes]
  (->> lingraph
       (reduce (fn[nodes n]
                 (if false ;(not (ins n))
                   nodes
                   (let [invec (ins n)
                         node (nodes n)
                         inputs-as-args (inputs-as-args? node)
                         node (cond
                               (empty? invec) node

                               inputs-as-args (assoc node :inputs-as-args true)

                               :else (assoc node
                                       :inputs (mapv #((nodes %) :id) invec)))
                         node (update-in
                               node [:args]
                               (fn[oargs]
                                 (replace
                                  (replacement-map
                                   #(vector %1 ((nodes %2) :url)) "%" invec)
                                  oargs)))
                         node (add-output-pipes n node edges ins)
                         node (add-input-pipes node invec nodes)
                         node (update-in
                               node [:args]
                               (fn[oargs]
                                 (replace
                                  (replacement-map #(vector %1 %2) "#" args)
                                  oargs)))
                         nodes (if inputs-as-args
                                 (apply dissoc nodes invec)
                                 nodes)]
                     (assoc nodes n node))))
               nodes)
       (mapv (fn[[n node]] [(node :id) node]))
       (into {})))

(defn config-inputs-unrolled [lingraph edges ins args nodes]
  #_(prn :CIU lingraph edges ins args nodes)
  (let [N (get-unrolled-node-map lingraph edges ins args nodes)
        all-edges edges #_(into edges
                        (->> N
                             (mapv (fn[[k m]] (vector (m :id) (m :inputs))))
                             (mapv (fn[[k v]] (mapv #(do {% [k]}) v)))
                             flatten))
        ;;_ (prn :ALL-EDGES all-edges)
        all-nodes (merge N nodes)
        E (reduce (fn[E [n ns]]
                    (let [nd (all-nodes n)]
                      (if (and nd (N (nd :id)))
                        (assoc E (nd :id)
                               (mapv (fn[m] ((all-nodes m) :id)) ns))
                        E)))
                  {} all-edges)
        outid (-> lingraph last all-nodes :id)]
    #_(prn :OI outid :E edges :-> E :N N)
    [outid N E]))


(def pgm-graph-schema
     {:nodes {sch/Keyword
              {:name sch/Str
               :type sch/Str
               (sch/optional-key :inputs) [sch/Str]
               (sch/optional-key :args)   [sch/Any]
               sch/Keyword sch/Any}}
      (sch/optional-key :edges) {sch/Keyword [sch/Keyword]}})

(defn xform-node-graph [G get-toolinfo inputs]
  #_(prn :>>> :GRAPH graph :INPUTS inputs)
  (let [args (G :args)
        tname (G :name)
        {:keys [graph path func src repeat]} (get-toolinfo tname)
        tgraph graph ; better name here
        subgraph? (not (nil? tgraph))
        tgraph (cond
                (= tname "webstrm")
                (reduce (fn[G [ks v]] (update-in G ks (fn[_] v)))
                        tgraph [[[:i1] (first inputs)]
                                [[:webstrm] G]])
                subgraph? tgraph
                :else
                {(keyword (gensym "t"))
                 (assoc G
                        :tool path :path path
                        :func func :src src :repeat repeat
                        :id (gensym tname)
                        :inputs (mapv :id inputs))})
        data (node-graph tgraph get-toolinfo inputs args)
        [outid ns es :as info] (apply config-inputs-unrolled data)]
    [subgraph? info]))

(defn xform-edges [edges inkeys k outid]
  #_(prn :XE edges inkeys k outid)
  (let [edges (reduce (fn[E ik] (assoc E ik (replace {k outid} (E ik))))
                      edges inkeys)
        edges (if (edges k) (assoc edges outid (edges k)) edges)]
    (dissoc edges k)))

(defn config-pgm-graph-nodes
  [graph get-toolinfo backstream-clients msgpacket]
  (let [graph (sch/validate pgm-graph-schema graph)
        nodes (graph :nodes)
        edges (graph :edges)
        ins (edges->ins edges)
        starts (set/difference (-> edges keys set) (-> ins keys set))
        lingraph (first (top-sort-nodes edges ins starts))]
    (reduce (fn[[nodes edges ins] [k graph]]
              #_(prn :NODES-1 nodes :EDGES edges :INS ins)
              (cond
               (#{"tool" "func" "stream"} (graph :type))
               (let [inkeys (ins k)
                     inputs (mapv #(nodes %) inkeys)
                     nodes (apply dissoc nodes inkeys)

                     [subgraph? [outid ns es]] (xform-node-graph
                                                graph get-toolinfo inputs)
                     ;;_ (prn :ES es)
                     edges (xform-edges edges inkeys k outid)
                     edges (if subgraph? (merge edges es) edges)
                     ;;_ (prn :EDGES edges)
                     ins (edges->ins edges)
                     nodes (merge (dissoc nodes k) ns)]
                 #_(prn :NODES-2 nodes edges ins)
                 [nodes edges ins])

               (#{"url" "bsurl"} (graph :type))
               (let [n (assoc graph :id (gensym "http"))
                     qid (-> (graph :url) (str/split #"client\?&id=") second)
                     proxclient (when qid (@backstream-clients qid))
                     n (if qid
                         (assoc n
                           :bsc proxclient :qid qid
                           :msgpacket msgpacket)
                         n)
                     edges (xform-edges edges [] k (n :id))
                     nodes (assoc (dissoc nodes k) (n :id) n)
                     ins (edges->ins edges)]
                 #_(prn :NODES-3 nodes edges ins)
                 [nodes edges ins])

               :else
               [nodes edges ins]))
            [nodes edges ins] (map #(vector % (nodes %)) lingraph))))


(defn config-pgm-graph [[nodes edges ins]]
  (let [starts (set/difference (-> edges keys set) (-> ins keys set))
        lingraph (first (top-sort-nodes edges ins starts))]
    (->> (config-inputs-unrolled lingraph edges ins [] nodes)
         rest
         vec)))

;;; --------------------------------------------------------------------------



(defn get-tool [path]
  (cond
   (not= "" (as-> path x (shl/proc "which" x)
                  (shl/stream-to-string x :out)
                  (str/trim x)))
   path

   (-> (fs/pwd) (fs/join "bin" path) fs/file?)
   (-> (fs/pwd) (fs/join "bin" path))

   :else
   (throw (ex-info "No such tool found" {:tool path}))))


(defn make-url-core [x]
  (->> x :url io/as-url input-stream (assoc {} :url)))

(defn make-tool-core [x]
  (let [x (if (-> x :cmd first fs/basename (= "bamstatsAlive") not)
            x
            (assoc x :cmd
                   (conj (-> x :cmd vec)
                         :env {"LD_LIBRARY_PATH" (fs/join (fs/pwd) "bin")})))
        proc (into (apply shl/proc (x :cmd))
                   (filterv (fn[[k v]] (#{:inpipes :outpipes} k)) x))
        inpipes (proc :inpipes)]
    (if (seq inpipes)
      (assoc proc :instrms (mapv io/output-stream inpipes))
      proc)))

(defn make-func-core [x]
  (assoc x :func (eval (x :src))))

(defn make-node-core [x]
  (debugf "MAKE-NODE-CORE: %s, %s" (:id x) (type x))
  (let [ncore
        (cond (stream? x) x
              (bsurl? x)  x
              (url? x)    (make-url-core x)
              (tool? x)   (make-tool-core x)
              (func? x)   (make-func-core x)
              :else
              (assert false (str "MAKE-NODE-CORE: unknown core of type "
                                 (type x))))]
    (assoc ncore :id (get ncore :id (x :id)))))


(defn make-flow-graph [pipeline-config]
  (let [[N E] pipeline-config ;_ (prn :NODES N)
        tools (->> N (keep (fn[[k v]] #_(prn :K k :V v)
                             (when-not (v :url)
                               (if (= (v :tool) "stream")
                                 [k (v :stream)]
                                 (let [m {:id (v :id)
                                          :inpipes (v :inpipes)
                                          :outpipes (v :outpipes)}]
                                   [k (if (v :func)
                                        (assoc m :func (v :func)
                                               :src (v :src)
                                               :args (v :args)
                                               :repeat (v :repeat))
                                        (assoc m :cmd
                                               (apply vector
                                                      (get-tool (v :tool))
                                                      (v :args))))])))))
                   (into {})) ;_ (prn :TOOLS tools)
        base-inputs (apply dissoc N (keys tools))
        node-outs
        (into {} (mapv (fn[[k v]]
                         [k (into {} (mapv #(vector % (async/chan 10)) v))])
                       E))
        node-ins
        (->> node-outs vals
             (reduce (fn[node-ins m]
                       (reduce (fn[node-ins [k v]]
                                 (assoc node-ins k
                                        (conj (get node-ins k []) v)))
                               node-ins m))
                     {}))]
    #_[node-outs node-ins]
    #_(mapv (fn[k] [k (node-ins k) (vec (vals (node-outs k)))]) (keys N))
    (mapv (fn[k]
            (let [nodecore (make-node-core (or (tools k) (base-inputs k)))
                  nodecore (if (not (proc? nodecore))
                             nodecore
                             (let [inputs (or (seq (nodecore :instrms))
                                              [(nodecore :in)])]
                               (assoc nodecore
                                 :chans->inputs
                                 (->> (interleave (node-ins k) inputs)
                                      (partition-all 2) (mapv vec)))))]
              [nodecore (node-ins k) (vec (vals (node-outs k)))]))
          (keys N))))




(defmacro go>!chans [chs value]
  `(go-loop [[ch# & chs#] ~chs, more# []]
     (if ch#
       (if (>! ch# ~value)
         (recur chs# (conj more# ch#))
         (recur chs# more#))
       more#)))

(defmacro go<!chans [chs chsym datasym & body]
  `(go-loop [[~chsym & chs#] ~chs, more# []]
     (if ~chsym
       (if-let [~datasym (<! ~chsym)]
         (do ~@body (recur chs# (conj more# ~chsym)))
         (recur chs# more#))
       more#)))

(defn aerobioerr? [x]
  (and (map? x) (x ::aerobioerr) (= (x ::aerobioerr) :aerobioerr)))

(defn aerobiosuccess? [x]
  (and (map? x) (x ::status) (= (x ::status) :success)))

(defn aerobioerr-ret [info]
  {::aerobioerr :aerobioerr ::status :aerobioerr :info info})

(defn exit-info [proc]
  (let [exitcode (shl/exit-code proc)]
    (if (= exitcode 0)
      {:id (proc :id) ::status :success}
      (aerobioerr-ret
       {:type "alert",
        :info {:id (proc :id)
               :exit exitcode
               :msg (shl/stream-to-string proc :err)}}))))

(defn exit-map? [x]
  (and (map? x) (x ::status)))

(defn eoi? [x]
  (or (exit-map? x) (= x ::done)))


(defn n2one? [func-node]
  (func-node :n->1))

(defn one2n? [func-node]
  (func-node :1->n))

(defn need
  "Inside function nodes, call this to indicate more values are needed
  to generate next output."
  []
  ::nextinput)

(defn need? [res]
  (= res ::nextinput))


(defn generator? [func-node]
  (func-node :repeat))

(defn more
  "Inside 'generator/repeat' function nodes, call this to indicate
  there are more values to return."
  [] ::more)

(defn more?
  [res]
  (= res ::more))

(defn done
  "Inside 'generator/repeat' function nodes, call this to indicate
  there are no more return values."
  [] ::done)

(defn done?
  [res]
  (= res ::done))



;;;(ns-unmap 'aerobio.pgmgraph 'job-node)
(defmulti
  ^{:doc "Dispatch program node graph creation based on node core type"
     :arglists '([node-core inputs outputs])}
  job-node
  (fn [node-core inputs outputs]
    (cond (proc? node-core) :proc
          (func? node-core) :func
          (stream? node-core) :stream
          (bsurl? node-core) :backstream
          (url? node-core) :url
          :else
          (assert false (str "JOB-NODE: unknown node of type "
                             (type node-core) "\n" node-core)))))


(defmethod job-node :backstream
  [urlmap _ outputs]
  (debugf "JOB-NODE, BACKSTREAM %s" urlmap)
  (let [msgpacket (urlmap :msgpacket)
        proxclient (urlmap :bsc)
        qid (urlmap :qid)
        ;;out (io/output-stream (fs/join (fs/pwd) "cache" "backstream.bam"))
        stream  {:clients-db-from-somewhere (proxclient :id)
                 :event "backstream", :params {:id qid}}
        on (stream :on)]
    (infof "!!! Start getting backstream data...")
    (on :onData
        (fn[data]
          #_(infof "!!! Got Backstream Data %s" data)
          #_(.write out data)
          (go-loop [[ch & chs] outputs]
            (when ch
              (>! ch data)
              (recur chs)))))
    (on :onEnd
        (fn[& args]
          (infof "$$$ Got End Event on Backstream")
          #_(.close out)
          (mapv async/close! outputs)))))


(defmethod job-node :url
  [urlmap _ outputs]
  (debugf "JOB-NODE, URL %s" urlmap)
  (let [input (urlmap :url)
        bufsize (* 64 1040)
        buf (byte-array bufsize)]
    (loop [outputs outputs
           n (.read input buf)
           tb 0]
      (if (or (neg? n) (empty? outputs))
        (do (mapv async/close! outputs)
            (.close input) tb)
        (let [slice (Arrays/copyOfRange buf 0 n)
              goch (go>!chans outputs slice)
              more (<!! goch)]
          (recur more (.read input buf) (+ tb n)))))))


(defn errfn [proc outputs]
  (let [errout (proc :err)
        pid (proc :id)
        bufsize (* 64 1040)
        buf (byte-array bufsize)]
    (go-loop [n (.read errout buf)]
      (if (neg? n)
        (do #_(mapv async/close! outputs)
            (.close errout))
        (let [msg (String. (Arrays/copyOfRange buf 0 n))
              err (aerobioerr-ret {:type "warn" :info {:id pid :msg msg}})]
          (infof "%s: %s" pid msg)
          (doseq [ch outputs] (>! ch err))
          (recur (.read errout buf)))))))

(defn outfn [instream outputs & {:keys [cache]}]
  (let [bufsize (* 64 1040)
        buf (byte-array bufsize)]
    (if cache
      (let [out (io/output-stream (fs/join (fs/pwd) "cache" cache))]
        (loop [outputs outputs
               n (.read instream buf)]
          (if (or (neg? n) (empty? outputs))
            (do #_(mapv async/close! outputs)
                (.close instream) (.close out))
            (let [slice (Arrays/copyOfRange buf 0 n)
                  _ (.write out slice)
                  goch (go>!chans outputs slice)
                  more (<!! goch)]
              (recur more (.read instream buf))))))
      (loop [outputs outputs
             n (.read instream buf)]
        (if (or (neg? n) (empty? outputs))
          (do #_(mapv async/close! outputs)
              (.close instream))
          (let [slice (Arrays/copyOfRange buf 0 n)
                goch (go>!chans outputs slice)
                more (<!! goch)]
            (recur more (.read instream buf))))))))

;; loop to read from all chans in inputs; write to (node-core :in)
;; read (node-core :ot) and write to all chans in outputs Will need
;; to have timeout chan for reading (node-core :ot)
(defmethod job-node :proc
  [proc inputs outputs]
  (let [input (proc :in)
        input-map (into {} (proc :chans->inputs))
        output (proc :out)
        cachefile (str (gensym) ".bam")
        bufsize (* 64 1040)
        buf (byte-array bufsize)]
    (if (seq inputs)
      (let [[outfut errfut] [(future+ (outfn output outputs :cache false))
                             (future+ (errfn proc outputs))]]
        (loop [chs inputs]
          (let [goch (go<!chans chs ch data
                      (try
                        (cond
                         (byte-array? data) (.write (input-map ch) data)
                         (aerobioerr? data) (doseq [ch outputs] (>! ch data))
                         :else :noop)
                        (catch Exception e
                          (warnf "%s, Exception on write: %s"
                                 (proc :id) e)
                          (async/close! ch)
                          #_(throw e))))
                more (<!! goch)]
            (if (seq more)
              (recur more)
              (let [_ (doseq [[ch s] (proc :chans->inputs)] (.close s))
                    _ (doseq [p (proc :inpipes)] (fs/delete p))
                    ei (exit-info proc)]
                (doseq [ch outputs] (>!! ch ei))
                (mapv async/close! outputs)
                (infof "Exit: %s" ei))))))
      (go-loop [n (.read output buf)]
        (if (neg? n)
          (let [_ (.close input)
                ei (exit-info proc)]
            (doseq [ch outputs] (>!! ch ei))
            (mapv async/close! outputs)
            (.close output))
          (let [slice (Arrays/copyOfRange buf 0 n)]
            (doseq [ch outputs] (>! ch slice))
            (recur (.read output buf))))))))


(defmethod job-node :func
  [funcm inputs outputs]
  (let [func (funcm :func)
        args (eval (funcm :args))
        repeat? (funcm :repeat)
        nto1 (n2one? funcm)
        one2n (one2n? funcm)
        short-circuit? (volatile! false)
        truefn (constantly true)]
    (cond
      (seq inputs)
      (loop [chs inputs]
        (let [goch (go<!chans
                    chs ch data
                    (try
                      #_(infof "%s: Data %s" (funcm :id) (type data))
                      (cond

                        repeat?
                        (loop [res (apply func (concat args [data]))]
                          (cond
                            (done? res) :nop ; repeat same as non
                            (need? res) :nop ; next data comes from channels
                            (more? res) :requires-mult-sigs-how-does-this-work?
                            :else
                            (when res
                              (doseq [ch outputs] (>! ch res)))))
                        :else
                        (let [res (apply func (concat args [data]))]
                          (cond
                            (need? res) :nop ; outer loop gets next data set
                            (done? res) (vswap! short-circuit? truefn)
                            :else
                            (when res
                              (doseq [ch outputs] (>! ch res))))))
                      (catch Exception e
                        (warnf "%s, Exception %s\n %s\n %s"
                               (funcm :id) e args data))))
              more (<!! goch)]
          (if (seq more)
            (recur more)
            (let [res (apply func (concat args [(done)]))]
              (when res (doseq [ch outputs] (>!! ch res)))
              (doseq [ch outputs] (async/close! ch))
              (infof "End: %s" (funcm :id))))))

      repeat?
      (loop [result (apply func args)]
        (cond
          (done? result) (doseq [ch outputs] (async/close! ch))

          (more? result) (recur (apply func args))

          :else
          (do (doseq [ch outputs] (>!! ch result))
              (recur (apply func args)))))

      :else
      (let [result (apply func args)]
        (infof "End: %s: %s" (funcm :id) result)
        (go
          (doseq [ch outputs]
            (>! ch result)
            (async/close! ch)))
        result))))



;;; stream - loop to read from all chans in inputs; ((stream :write)
;;; :data x))
(defmethod job-node :stream
  [stream inputs _]
  (let [bufsize (* 64 1040)
        buf (byte-array bufsize)
        write (stream :write)
        end (stream :end)
        utf8 (= "utf8" (get-in stream [:params :encoding]))]
    (infof "PIPE-TO-STREAM utf8 %s" utf8)
    (loop [chs inputs]
      (let [goch (go-loop [[ch & chs] chs
                           more []]
                   (if ch
                     (when-let [data (<! ch)]
                       (cond
                        (aerobioerr? data) (write :error data)
                        (aerobiosuccess? data) nil ; NOP
                        :else
                        (let [payload (if utf8 (String. data) data)]
                          (write :data payload)))
                       (recur chs (conj more ch)))
                     more))
            more (<!! goch)]
        (if (seq more)
          (recur more)
          (end))))))


(defn run-flow-program
  "Takes a data flow graph, as produced by a 'make-pipeline',
   activates the nodes and runs the graph to completion
  "
  [dfg]
  (mapv (fn[node]
          (future+
           (let [jnres (apply job-node node)]
             (if (proc? (first node))
               (let [exit-code (shl/exit-code (first node))
                     msg (if (= 0 exit-code)
                           :success
                           (shl/stream-to-string (first node) :err))]
                 (infof "%s: exit code: %s\n%s"
                        (-> node first :id)
                        exit-code
                        msg))
               (infof "Done, node %s" (-> node first :id)))
             jnres)))
        dfg))


(comment

  (init-pgms)

  (loop [v (<!! urlch)]
    (if (not v)
      :exit
      (do (println v)
          (recur (<!! urlch)))))
  )
