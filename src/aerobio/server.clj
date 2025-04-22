;;--------------------------------------------------------------------------;;
;;                                                                          ;;
;;                     A E R O B I O . S E R V E R                          ;;
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

(ns aerobio.server
  "Streaming job server with full tree graphs, function nodes, superior
   error handling, logging, caching, etc.
  "

  {:author "Jon Anthony"}

  (:require
   [clojure.string :as str]
   [clojure.pprint :refer [pprint]]
   [clojure.set :as set]
   [clojure.data.csv :as csv]
   [clojure.data.json :as json]
   [clojure.java.io :as io]
   [clojure.tools.reader.edn :as edn]
   [clojure.core.async :as async
    :refer (<! >! <!! >!! put! chan go go-loop)]

   [ring.middleware.defaults]
   [ring.middleware.gzip :refer [wrap-gzip]]
   [ring.middleware.cljsjs :refer [wrap-cljsjs]]
   [ring.util.response :refer (resource-response content-type)]
   #_[ring.util.codec :as ruc]

   [compojure.core :as comp :refer (routes GET POST)]
   [compojure.route :as route]

   [hiccup.core :as hiccup]

   [taoensso.timbre :as timbre
    :refer (tracef debugf infof warnf errorf)]

   ;; Watch tool config directory to automatically configure new tools
   [clojure-watch.core :refer [start-watch]]

   [com.rpl.specter :as sp]

   [aerial.hanasu.server :as srv]
   [aerial.hanasu.common :as hc]

   [tablecloth.api :as tc]

   [aerial.fs :as fs]
   [aerial.utils.misc :as aum]
   [aerial.utils.io :as aio]
   [aerial.utils.string :as austr]
   [aerial.utils.coll :as coll :refer [in takev-until dropv-until ensure-vec]]

   ;; For pgm graph data shape validation
   [schema.core :as sch]

   ;; Our parameter db
   [aerobio.params :as pams]
   ;; HTSeq
   [aerobio.htseq.common  :as cmn]
   [aerobio.htseq.rnaseq  :as htrs]
   [aerobio.htseq.termseq :as httm]
   [aerobio.htseq.tnseq   :as htts]
   [aerobio.htseq.wgseq   :as htws]
   ;; Validation
   [aerobio.validate.all :as va]
   [aerobio.validate.datasets :as vds]
   ;; Program graph construction, execution, delivery
   [aerobio.pgmgraph :as pg]
   ;; REST actions
   [aerobio.actions :as actions]
   ))




(defn date-time-map []
  (let [dt (->> (java.time.LocalDateTime/now) str (austr/split #"T")
                (mapv #(vector %1 %2) [:date :time]) (into {}))
        time (->> dt :time (austr/split #":")
                  (mapv #(vector %1 %2) [:hour :min :sec]) (into {}))
        dt (assoc dt :time-bits time)]
    dt))

(defn date-time-stg []
  (let [dt (date-time-map)]
    (str (dt :date) "-"
         (str/join ":" [(-> dt :time-bits :hour) (-> dt :time-bits :min)]))))

;;; ------------------------------------------------------------------------;;;
;;;                    Tool Config and  Watcher                             ;;;
;;; ------------------------------------------------------------------------;;;

(def tool-configs (atom nil))

(defn bind-env [f env]
  (let [val (env :value)
        done? (env :done)]
    (if done?
      env
      (let [val (f val)]
        (assoc env :value val :done? (or (nil? val) (= val :done)))))))

(defn read-tool-config [f]
  (infof "Tool update: %s" f)
  (binding [*ns* (find-ns 'aerobio.server)]
    (if (= "clj" (fs/ftype f))
      (let [cfg (-> f slurp clojure.core/read-string)
            func (cfg :func)]
        (if (and func (or (symbol? func) (list? func)))
          (assoc cfg :func (eval func) :src func)
          cfg))
      (-> f slurp (json/read-str :key-fn keyword)))))

(defn read-tool-configs
  []
  (let [wd (fs/pwd)
        srvdir (fs/join wd "services")
        _ (assert (fs/directory? srvdir)
                  (format "Services dir %s missing!!" srvdir))
        configs (fs/re-directory-files srvdir #"\.(clj|js)")
        toolinfo (reduce
                  (fn[M f]
                    (assoc M (-> f fs/basename (str/split #"\.") first)
                           (read-tool-config f)))
                  {} configs)]
    toolinfo))


(def _watcher (atom nil))

(defmulti
  ^{:doc "Dispatch dynamic tool config based on service file event"
    :arglists '([event filename])}
  config-tool
  (fn [event filename] event))


(defmethod config-tool :create
  [_ f]
  (infof "Service Create %s" f)
  (swap! tool-configs
         assoc (-> f fs/basename (str/split #"\.") first)
         (read-tool-config f)))

(defmethod config-tool :modify
  [_ f]
  (infof "Service Modify %s" f)
  (swap! tool-configs
         assoc (-> f fs/basename (str/split #"\.") first)
         (read-tool-config f)))

(defmethod config-tool :delete
  [_ f]
  (infof "Service Delete %s" f)
  (swap! tool-configs
         dissoc  (-> f fs/basename (str/split #"\.") first)))

(defn stop-tool-watcher []
  (when-let [stopw @_watcher] (stopw)))

(defn start-tool-watcher []
  (stop-tool-watcher)
  (reset! _watcher
          (start-watch
           [{:path (fs/join (fs/pwd) "services")
             :event-types [:create :modify :delete]
             :bootstrap (fn[_](swap! tool-configs (fn[_] (read-tool-configs))))
             :callback config-tool
             :options {:recursive true}}])))


(defn get-toolinfo [toolname eid]
  (assert (@tool-configs toolname)
          (format "No such tool %s" toolname))
  (let [args (if (coll/in eid (cmn/exp-ids))
               ((cmn/get-exp-info eid :cmdsargs) toolname {})
               {})]
    (merge {:inputOption "" :options "" :args "" :argcard {}}
           (@tool-configs toolname)
           {:args args})))


;;; ------------------------------------------------------------------------;;;
;;;                     Job Config and  Watcher                             ;;;
;;; ------------------------------------------------------------------------;;;

(def job-configs (atom nil))

(defn read-job-config [f]
  (infof "Job update: %s" f)
  (if (= "clj" (fs/ftype f))
    (let [cfg (-> f slurp edn/read-string)
          func (cfg :func)]
      (if (and func (or (symbol? func) (list? func)))
        (assoc cfg :func (eval func) :src func)
        cfg))
    (-> f slurp (json/read-str :key-fn keyword))))

(defn read-job-configs
  []
  (let [wd (fs/pwd)
        jobdir (fs/join wd "Jobs")
        _ (assert (fs/directory? jobdir)
                  (format "Jobs dir %s missing!!" jobdir))
        configs (fs/re-directory-files jobdir #"\.(clj|js)")
        jobinfo (reduce
                  (fn[M f]
                    (assoc M (-> f fs/basename (str/split #"\.") first)
                           (read-job-config f)))
                  {} configs)]
    jobinfo))


(def _job-watcher (atom nil))

(defmulti
  ^{:doc "Dispatch dynamic job config based on job file event"
    :arglists '([event filename])}
  config-job
  (fn [event filename] event))


(defmethod config-job :create
  [_ f]
  (infof "Job Create %s" f)
  (swap! job-configs
         assoc (-> f fs/basename (str/split #"\.") first)
         (read-job-config f)))

(defmethod config-job :modify
  [_ f]
  (infof "Job Modify %s" f)
  (swap! job-configs
         assoc (-> f fs/basename (str/split #"\.") first)
         (read-job-config f)))

(defmethod config-job :delete
  [_ f]
  (infof "Job Delete %s" f)
  (swap! job-configs
         dissoc  (-> f fs/basename (str/split #"\.") first)))

(defn stop-job-watcher []
  (when-let [stopw @_job-watcher] (stopw)))

(defn start-job-watcher []
  (stop-job-watcher)
  (reset! _job-watcher
          (start-watch
           [{:path (fs/join (fs/pwd) "Jobs")
             :event-types [:create :modify :delete]
             :bootstrap (fn[_](swap! job-configs (fn[_] (read-job-configs))))
             :callback config-job
             :options {:recursive true}}])))

(defn job-exists? [jobname]
  (@job-configs jobname))

(defn get-job-name [exp cmd work-item]
  (if (= cmd :run)
    (str (name exp) "-" work-item "-job-template")
    (str (name exp) "-" (name cmd)"-job-template")))

(defn validate-job [exp cmd work-item]
  (let [jobname (get-job-name exp cmd work-item)]
    (if (job-exists? jobname)
      ""
      (if (= cmd :run)
        (format "No job for experiment type '%s' with cmd '%s' for phase '%s'"
                (name exp) (name cmd) work-item)
        (format "No job for experiment type '%s' with cmd '%s'"
                (name exp) (name cmd))))))

(defn get-jobinfo [jobname]
  (assert (@job-configs jobname)
          (format "No such job %s" jobname))
  (@job-configs jobname))




;;; ------------------------------------------------------------------------;;;
;;;                     Server communication database                       ;;;
;;; ------------------------------------------------------------------------;;;

(defn uuid [] (str (java.util.UUID/randomUUID)))

(defonce app-bpsize 100)
(defonce app-db (atom {:rcvcnt 0 :sntcnt 0}))

(defn update-adb
  ([] (hc/update-db app-db {:rcvcnt 0 :sntcnt 0}))
  ([keypath vorf]
   (hc/update-db app-db keypath vorf))
  ([kp1 vof1 kp2 vof2 & kps-vs]
   (apply hc/update-db app-db kp1 vof1 kp2 vof2 kps-vs)))

(defn get-adb
  ([] (hc/get-db app-db []))
  ([key-path] (hc/get-db app-db key-path)))


;;; ------------------------------------------------------------------------;;;
;;;                               Job database                              ;;;
;;; ------------------------------------------------------------------------;;;

(defonce job-db (atom {}))

(defn read-job-db []
  (let [dir (fs/fullpath (pams/get-params [:jobs :dir]))
        f (fs/join dir (pams/get-params [:jobs :file]))]
    (->> f slurp edn/read-string (reset! job-db))))

(defn valuefy [db]
  (sp/transform
   sp/ALL
   (fn[v]
     (cond
       (isa? (type v) clojure.lang.Atom) (recur (deref v))
       (isa? (type v) clojure.core.async.impl.channels.ManyToManyChannel) :chan
       (coll? v) (valuefy v)
       (not (future? v)) v
       (future-done? v) (deref v)
       :else :future-not-yet-finished))
   db))

(defn write-job-db []
  (let [dir (fs/fullpath (pams/get-params [:jobs :dir]))
        f (fs/join dir (pams/get-params [:jobs :file]))]
    (aio/with-out-writer f
      (prn (valuefy @job-db)))))

(defn update-jdb
  ([] (hc/update-db job-db {}))
  ([keypath vorf]
   (hc/update-db job-db keypath vorf))
  ([kp1 vof1 kp2 vof2 & kps-vs]
   (apply hc/update-db job-db kp1 vof1 kp2 vof2 kps-vs)))

(defn get-jdb
  ([] (hc/get-db job-db []))
  ([key-path] (hc/get-db job-db key-path)))


(defn get-job-status [user ])




;;; ------------------------------------------------------------------------;;;
;;;                      Aerobio Application Messaging...                   ;;;
;;; ------------------------------------------------------------------------;;;

(defn send-end-msg [ws msg]
  (srv/send-msg ws msg)
  (srv/send-msg ws {:op :stop :payload {}} :noenvelope true))


(def dbg (atom {}))


(defmulti user-msg :op)

(defmethod user-msg :default [msg]
  (let [params (msg :params)
        {:keys [ws user eid action]} params]
    (infof "ERROR: unknown user message %s" msg)
    (send-end-msg
     ws {:op :error
         :payload (format "unknown user-msg '%s'" (msg :op))})))


(defmethod user-msg :status [msg]
  (let [params (msg :params)
        {:keys [ws user eid action]} params]
    #_(infof "STATUS %s" params)
    (let [jinfo (hc/get-db (atom (valuefy @job-db)) [user eid action])
          result (if (seq jinfo)
                   (->> jinfo (sort-by #(-> % :request :timestamp)
                                       austr/string-greater?)
                        (mapv #(vector (-> % :request :timestamp)
                                       (% :status)
                                       (% :result))))
                   (format "No job information for [%s %s %s]"
                           user eid action))
          retmsg (if (string? result)
                   result
                   (str/join
                    "\n"
                    (mapv #(let [timestamp (first %)
                                 status (second %)
                                 res (last %)
                                 rstg (if (= res :future-not-yet-finished)
                                        (format
                                         "Currently still running:\n%s"
                                         (with-out-str (pprint status)))
                                        (format
                                         "Finished:\n%s\nFinal result:\n%s"
                                         (with-out-str
                                           (pprint (status :DONE [])))
                                         (with-out-str
                                           (pprint res))))]
                             (with-out-str
                               (print (format "%s : %s started @ %s\n%s"
                                              eid action timestamp rstg))))
                          result)))]
      (send-end-msg ws {:op :status :payload retmsg}))))


(defn user-msg-body [ws user eid action params]
  (let [action (name action)
        vmsg (va/validate-exp eid action)]
    (if (not-empty vmsg)
      (send-end-msg ws {:op :validate :payload vmsg})
      (let [{:keys [cmd eid template]} params
            dt (date-time-stg)
            get-toolinfo #(get-toolinfo % eid)
            resmap (actions/action cmd eid params get-toolinfo template)
            resfut (resmap :fut)
            jobdb-info (or (get-jdb [user eid action]) [])]
        (aerial.utils.misc/sleep 250)
        (swap! dbg (fn[M] (assoc M eid resmap)))
        (infof "%s : %s" cmd (prn-str resfut))
        (update-jdb [user eid action]
                    (conj jobdb-info
                          {:request {:timestamp dt
                                     :params (dissoc params :ws)}
                           :status (resmap :status)
                           :result resfut}))
        (if (and (future-done? resfut) (map? @resfut) (@resfut :error))
          (let [cause (->> (or (@resfut :msg) (prn-str (@resfut :error)))
                           (str "Job Launch : "))]
            (srv/send-msg ws {:op :error :payload cause}))
          (srv/send-msg ws {:op :launch :payload "Successful"}))
        (srv/send-msg ws {:op :stop :payload {}} :noenvelope true)))))


(defmethod user-msg :aggregate [msg]
  (let [params (msg :params)
        {:keys [ws user eid compfile]} params]
    (infof "AGGREGATE: %s" params)
    (user-msg-body ws user eid (params :cmd) params)
    #_(send-end-msg ws {:op :status :payload "Aggregating"})))

(defmethod user-msg :xcompare [msg]
  (let [params (msg :params)
        {:keys [ws user eid compfile]} params
        base (fs/join (pams/get-params :scratch-base) eid)]
    (infof "XCOMPARE: %s" params)
    (cmn/init-xcompare-exp eid compfile)
    (user-msg-body ws user eid (params :cmd) params)
    #_(send-end-msg ws {:op :status :payload "Xcomparing"})))

(defmethod user-msg :compare [msg]
  (let [params (msg :params)
        {:keys [ws user eid compfile]} params]
    (infof "COMPARE: %s" params)
    (user-msg-body ws user eid (params :cmd) params)
    #_(send-end-msg ws {:op :status :payload "Comparing"})))


(defmethod user-msg :run [msg]
  (let [params (msg :params)
        {:keys [ws user eid phase]} params]
    (infof "RUN: %s" params)
    (let [vmsg (va/validate-exp eid phase)]
      (if (not-empty vmsg)
        (send-end-msg ws {:op :validate :payload vmsg})
        (let [{:keys [cmd eid template]} params
              dt (date-time-stg)
              get-toolinfo #(get-toolinfo % eid)
              resmap (actions/action cmd eid params get-toolinfo template)
              resfut (resmap :fut)
              jobdb-info (or (get-jdb [user eid phase]) [])]
          (aerial.utils.misc/sleep 250)
          (swap! dbg (fn[M] (assoc M eid resmap)))
          (infof "%s : %s" cmd (prn-str resfut))
          (update-jdb [user eid phase]
                      (conj jobdb-info
                            {:request {:timestamp dt
                                       :params (dissoc params :ws)}
                             :status (resmap :status)
                             :result resfut}))
          (if (and (future-done? resfut) (map? @resfut) (@resfut :error))
            (let [cause (->> (or (@resfut :msg) (prn-str (@resfut :error)))
                             (str "Job Launch : "))]
              (srv/send-msg ws {:op :error :payload cause}))
            (srv/send-msg ws {:op :launch :payload "Successful"}))
          (srv/send-msg ws {:op :stop :payload {}} :noenvelope true))))))


(defmethod user-msg :check [msg]
  (let [params (msg :params)
        {:keys [ws user eid]} params]
    (infof "CHECK: %s" params)
    (let [vmsg (vds/validate-expexists eid)
          vmsg (if (empty? vmsg) (va/validate-sheets-exist eid "check") vmsg)]
      (if (not-empty vmsg)
        (send-end-msg ws {:op :validate :payload vmsg})
        (let [_ (cmn/set-exp eid)
              vmsg (va/validate-exp eid "all")]
          (if (not-empty vmsg)
            (send-end-msg ws {:op :validate :payload vmsg})
            (send-end-msg ws {:op :validate :payload "All checks pass!"})))))))


(defmethod user-msg :job [msg]
  (let [{:keys [ws jobname args]} (msg :params)
        get-toolinfo #(get-toolinfo % "DummY")
        template (get-jobinfo jobname)
        {:keys [status fut]} (actions/action :job  args get-toolinfo template)]
    (aerial.utils.misc/sleep 250)
    (swap! dbg (fn[m] (assoc m :job fut)))
    (send-end-msg ws {:op :launch :payload (str jobname "\n" args "\n" fut)})))


(defn msg-handler [msg]
  (let [{:keys [ws data]} msg
        {:keys [op data]} data
        {:keys [user cmd phase modifier compfile eid action]} data
        eid (if (str/ends-with? eid "/") (austr/butlast 1 eid) eid)
        eid (if (str/starts-with? eid "/") (austr/drop 1 eid) eid)
        params {:user user, :cmd op, :eid eid
                :phase phase, :action action :compfile compfile
                :modifier modifier, :ws ws}]
    (infof "MSG-HANDLER (%s): MSG %s" *ns* (msg :data))
    #_(infof "PARAMS : %s" params)
    (try
      (cond
        (#{:done "done"} op)
        (do (infof "WS: %s, sending stop msg" ws)
            (srv/send-msg ws {:op :stop :payload {}} :noenvelope true))

        (#{:status :check} op)
        (user-msg {:op op :params params})

        (#{:run :compare :aggregate} op)
        (let [vmsg (vds/validate-expexists eid)
              vmsg (if (empty? vmsg) (va/validate-sheets-exist eid op) vmsg)]
          (if (not-empty vmsg)
            (send-end-msg ws {:op :validate :payload vmsg})
            (let [_ (cmn/set-exp eid)
                  exp (cmn/get-exp-info eid :exp)
                  vmsg (validate-job exp op (or phase compfile))]
              (if (not-empty vmsg)
                (send-end-msg ws {:op :validate :payload vmsg})
                (let [tempnm (get-job-name exp op (or phase compfile))
                      template (get-jobinfo tempnm)
                      params (assoc params :template template)]
                  (user-msg {:op op :params params}))))))

        (#{:xcompare} op)
        (let [vmsg (vds/validate-expexists eid)]
          (if (not-empty vmsg)
            (send-end-msg ws {:op :validate :payload vmsg})
            (let [tempnm (get-job-name :rnaseq op (or phase compfile))
                  template (get-jobinfo tempnm)
                  params (assoc params :template template)]
              (user-msg {:op op :params params}))))

        (job-exists? (name op))
        (user-msg {:op :job
                   :params {:ws ws :jobname (name op) :args (data :args)}})
        

        :else
        (send-end-msg
         ws {:op :error
             :payload (format "No such cmd/job '%s', args: '%s'"
                              (name op) (data :args))}))
      (catch Error e
        (errorf "ERROR %s: %s, %s" eid op (or (.getMessage e) e))
        (send-end-msg ws {:op :validate :payload (.getMessage e)}))
      (catch Exception e
        (errorf "EXCEPTION %s: %s, %s" eid op (or (.getMessage e) e))
        (send-end-msg ws {:op :validate :payload (.getMessage e)})))))


(defn on-open [ch op payload]
  (let [ws payload
        uid-name ((-> :idfn get-adb first))
        connfn (-> :connfn get-adb first)
        uuid (uuid)
        uid {:uuid uuid :name uid-name}
        data (connfn {:uid uid})
        name-uuids (or (get-adb uid-name) [])]
    (infof ":SRV :open %s" uid)
    (update-adb :ws ws
                [ws :uuid] uuid
                [uuid :ws] ws, [uuid :name] uid-name
                [uuid :rcvcnt] 0, [uuid :sntcnt] 0
                uid-name (conj name-uuids uuid))
    (srv/send-msg ws {:op :register :payload data})))


(defn server-dispatch [ch op payload]
  (case op
    :msg (let [{:keys [ws]} payload]
           (msg-handler payload)
           (update-adb :rcvcnt inc, [ws :rcvcnt] inc))

    :open (on-open ch op payload)
    :close (let [{:keys [ws status]} payload
                 uuid (get-adb [ws :uuid])
                 uuid-name (get-adb [uuid :name])
                 uuids (get-adb uuid-name)]
             (infof ":SRV :close :uuid %s, :uuid-name %s" uuid uuid-name)
             (update-adb ws :rm, uuid :rm
                         uuid-name (->> uuids (remove #(= uuid %)) vec)))

    :bpwait (let [{:keys [ws msg encode]} payload
                  uuid (get-adb [ws :uuid])]
              (infof ":SRV :uuid %s - Waiting to send msg %s" uuid msg)
              (update-adb [uuid :bpdata] {:msg msg, :encode encode}))
    :bpresume (let [{:keys [ws msg]} payload
                    uuid (get-adb [ws :uuid])
                    encode ((get-adb [uuid :bpdata]) :encode)]
                (infof ":SRV BP Resume :uuid %s" (get-adb [ws :uuid]))
                (srv/send-msg ws msg :encode encode))

    :sent (let [{:keys [ws msg]} payload]
            #_(infof ":SRV Sent msg %s" msg)
            (update-adb :sntcnt inc, [ws :sntcnt] 0))
    :failsnd (infof ":SRV Failed send for {:op %s :payload %s}" op payload)

    :stop (let [{:keys [cause]} payload]
            (infof ":SRV Stopping reads... Cause %s" cause)
            (update-adb)
            (srv/stop-server))
    (infof ":SRV :WTF {:op %s :payload %s}" op payload)))




;;; ------------------------------------------------------------------------;;;
;;;                Server routes, handlers, start and stop                  ;;;
;;; ------------------------------------------------------------------------;;;


(defn landing-handler [request index-path]
  #_(printchan request)
  (content-type
   {:status 200
    :body (io/input-stream (io/resource index-path))}
   "text/html"))

(defn aerobio-routes [& {:keys [landing-handler index-path]
                         :or {landing-handler landing-handler
                              index-path "public/index.html"}}]
  (apply routes
         (conj (srv/hanasu-handlers)
               (GET "/" request (landing-handler request index-path))
               (route/resources "/"))))

(defn aerobio-handler [aerobio-routes & middle-ware-stack]
  (reduce (fn[R mwfn] (mwfn R)) aerobio-routes middle-ware-stack))



(defn start-server
  [port & {:keys [route-handler idfn connfn]
           :or {route-handler (aerobio-handler (aerobio-routes))
                connfn identity}}]
  (let [ch (srv/start-server port :main-handler route-handler)]
    (infof "Server start, reading msgs from %s" ch)
    (update-adb :chan ch
                :idfn [idfn]
                :connfn [connfn])
    (go-loop [msg (<! ch)]
      (let [{:keys [op payload]} msg]
        (future (server-dispatch ch op payload))
        (when (not= op :stop)
          (recur (<! ch)))))))

(defn stop-server []
  (async/>!! (get-adb :chan) {:op :stop :payload {:cause :userstop}}))


(defn connfn [data] data)


(defn start! [port]
  (timbre/set-level! :info) ; :debug
  (start-tool-watcher)
  (start-job-watcher)
  (start-server
   port
   :idfn (constantly "Aerobio")
   :connfn connfn
   :route-handler
   (aerobio-handler (aerobio-routes :index-path "public/index.html"))))

(defn stop! []
  (stop-tool-watcher)
  (stop-job-watcher)
  (stop-server))

#_(start! 7070)
#_(stop-server)




(comment

  (let [eid "Ngon_cipro"
        repk :rep
        exp (cmn/get-exp-info eid :exp)
        sample-names (cmn/get-exp-info eid :sample-names)
        sample-names (if repk
                       (mapcat #((cmn/get-exp-info eid :replicate-names) %)
                               (cmn/get-exp-info eid :sample-names))
                       sample-names)]
    (partition-all 2 sample-names))


  (let [eid "Ngon_cipro"
        recipient "jsa"
        template {:nodes
                  {:ph0 {:name "rnaseq-phase0", :type "tool", :args []},
                   :prn1 {:type "func", :name "prn"}},
                  :edges {:ph0 [:prn1]}}
        get-toolinfo #(get-toolinfo % eid)
        ph0 template
        cfg (-> (assoc-in ph0 [:nodes :ph0 :args] [eid recipient])
                (pg/config-pgm-graph-nodes get-toolinfo nil nil)
                pg/config-pgm-graph)]
    cfg)
  )
