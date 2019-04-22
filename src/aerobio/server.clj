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
   [clojure.set :as set]
   [clojure.data.json :as json]
   [clojure.java.io :as io]
   [clojure.tools.reader.edn :as edn]
   [clojure.core.async :as async :refer (<! >! go go-loop)]

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
   ;; Program graph construction, execution, delivery
   [aerobio.pgmgraph :as pg]
   ;; REST actions
   [aerobio.actions :as actions]
   ))




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
  (swap! tool-configs
         assoc (-> f fs/basename (str/split #"\.") first)
         (read-tool-config f)))

(defmethod config-tool :modify
  [_ f]
  (swap! tool-configs
         assoc (-> f fs/basename (str/split #"\.") first)
         (read-tool-config f)))

(defmethod config-tool :delete
  [_ f]
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

(defn get-toolinfo [toolname]
  (assert (@tool-configs toolname)
          (format "No such tool %s" toolname))
  (merge {:inputOption "" :options "" :args ""}
         (@tool-configs toolname)))


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
  (swap! job-configs
         assoc (-> f fs/basename (str/split #"\.") first)
         (read-job-config f)))

(defmethod config-job :modify
  [_ f]
  (swap! job-configs
         assoc (-> f fs/basename (str/split #"\.") first)
         (read-job-config f)))

(defmethod config-job :delete
  [_ f]
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
;;;                   Aerobio Application Messaging...                      ;;;
;;; ------------------------------------------------------------------------;;;

(defmulti user-msg :op)

(defmethod user-msg :default [msg]
  (infof "ERROR: unknown user message %s" msg)
  #_(srv/send-msg
     (msg :ws) {:op "error" :payload "ERROR: unknown user message"}))

(defmethod user-msg :run [msg]
  (let [info (msg :payload)
        ws (info :ws)
        eid (info "eid")
        eid (if (str/ends-with? eid "/") (austr/butlast 1 eid) eid)
        eid (if (str/starts-with? eid "/") (austr/drop 1 eid) eid)]
    (infof "RUN: %s" info)
    (srv/send-msg ws {:op :validate :payload (va/validate-exp eid)})
    (srv/send-msg ws {:op :stop :payload {}} :noenvelope true)))

(defn msg-handler [msg]
  (let [{:keys [ws data]} msg
        {:keys [op data]} data]
    (infof "MSG-HANDLER: MSG %s" (msg :data))
    (case op
      (:done "done")
      (do (infof "WS: %s, sending stop msg" ws)
          (srv/send-msg ws {:op :stop :payload {}} :noenvelope true))
      ;;null for now
      (user-msg {:op op :payload (assoc data :ws ws)}))))


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

#_(start-server
   7070
   :route-handler (aerobio-handler
                   (aerobio-routes :index-path "public/index.html"))
   :idfn (constantly "Aerobio")
   :connfn connfn)
#_(stop-server)


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







(def dbg (atom {}))

(defn htseq-file-get [args reqmap]
  (infof "Args %s\nParams %s" args (reqmap :params))
  #_(infof "ReqMap %s" reqmap)
  (binding [*ns* (find-ns 'aerobio.server)]
    (let [params (reqmap :params)
          {:keys [user cmd action rep compfile eid]} params
          eid (if (str/ends-with? eid "/") (austr/butlast 1 eid) eid)
          eid (if (str/starts-with? eid "/") (austr/drop 1 eid) eid)
          user-agent (get-in reqmap [:headers "user-agent"])
          reqtype (get-in reqmap [:headers "reqtype"])]
      (if (= reqtype "cmdline")
        (try
          (let [_ (cmn/set-exp eid)
                exp (cmn/get-exp-info eid :exp)
                work-item (if action action cmd)
                tempnm (str (name exp) "-" work-item "-job-template")
                template (get-jobinfo tempnm)
                result (actions/action cmd eid params get-toolinfo template)]
            (aerial.utils.misc/sleep 250)
            (swap! dbg (fn[M] (assoc M eid result)))
            (infof "%s : %s" cmd result)
            (json/json-str
             {:params params
              :result (str result)
              :template tempnm
              :recipient user}))
          (catch Error e
            (let [estg (format "Error %s" (or (.getMessage e) e))]
              (errorf "%s: %s - Assert: %s" eid cmd estg)
              (json/json-str
             {:args args
              :params params
              :result estg
              :recipient user
              :user-agent user-agent
              :reqtype reqtype})))
          (catch Exception e
            (let [emsg (or (.getMessage e) e)]
              (errorf "%s: %s, Exception on Job launch: %s" eid cmd emsg)
              (json/json-str
               {:args args
                :params params
                :result (format "Exception: %s" emsg)
                :recipient user
                :user-agent user-agent
                :reqtype reqtype}))))
        (hiccup/html
         [:h1 "HI from HTSeq command route"]
         [:hr]
         [:p (str "command and args: " args)]
         [:hr]
         [:p (str "Params" params)]
         [:hr]
         [:p (str "User Agent: " user-agent)])))))
