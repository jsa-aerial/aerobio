;;--------------------------------------------------------------------------;;
;;                                                                          ;;
;;                        A E R O B I O . C O R E                           ;;
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

(ns aerobio.core
  "A robust, flexible streaming job server for open ended
  bioinformatic analyses. Capabilities for full DAG job flow graphs,
  function nodes, superior error handling, logging, caching, etc.

  Supports dynamic open ended services written in Clojure, R, Python,
  Perl, and any Linux/Unix command line tool. Supports nodes with
  multiple inputs and outputs - utilizing pipes in cases of tools not
  able to include multiple streaming input.
  "

  {:author "Jon Anthony"}

  (:gen-class)

  (:require [clojure.string :as cstr]
            [clojure.java.io :as io]
            [clojure.tools.reader.edn :as edn]

            ;; Command line arg processing
            [clojure.tools.cli :refer [parse-opts]]

            ;; Tunneling Cider nREPL support
            [clojure.tools.nrepl.server :as nrs]
            [cider.nrepl :refer [cider-middleware]]
            [refactor-nrepl.middleware :refer [wrap-refactor]]

            [cpath-clj.core :as cp] ; Extract Jar resources

            [aerial.fs :as fs]
            [aerial.utils.misc :refer [getenv]]
            [aerial.utils.coll :as coll]

            [aerial.bio.utils.params :as bpams]

            [taoensso.timbre    :as timbre
             :refer (tracef debugf infof warnf errorf)]

            [aerobio.params :as pams]
            [aerobio.server :as svr]
            [aerobio.pgmgraph :as pg])

  (:import [java.io File]))


(def cli-options
  ;; An option with a required argument
  [["-I" "--install DIR" "Directory to self install to"
    :default nil
    :parse-fn #(fs/fullpath %)
    :validate [#(fs/directory? %) "Must be a writable directory"
               #(fs/writeable? %) "Must be a writable directory"]]

   ["-p" "--port PORT" "Port number for http server"
    :default 8080
    :parse-fn #(Integer/parseInt %)
    :validate [#(< 0 % 0x10000) "Must be a number between 0 and 65536"]]

   ["-rp" "--repl-port PORT" "Port for nrepl server"
    :default 4005
    :parse-fn #(Integer/parseInt %)
    :validate [#(< 4000 % 5000) "Must be a number between 0 and 65536"]]
   ;; A boolean option defaulting to nil
   ["-h" "--help"]])

;; Incorporate correct cider middleware for tunneling nREPL support
(apply nrs/default-handler (map resolve cider-middleware))



(defn- digits? [s] (let [x (re-find #"[0-9]+" s)] (and x (= x s))))


(defn- get-install-dir
  "Query user for location that will be aerobio installation and home
   directory
  "
  []
  (print "Enter installation directory [~/.aerobio]: ")
  (flush)
  (let [input (read-line)
        input (fs/fullpath (if (= input "") "~.aerobio" input))]
    (println "Selected installation location:" input)
    input))

(defn install-aerobio
  "Create installation directory, mark it as the home directory and
   install required resources and set the host machine dns lookup name
   in websocket address. AEROBIODIR is the directory user gave on query
   for location to install.
  "
  [aerobiodir]
  (let [resmap #{"bam.aerobio.io" "vcf.aerobio.io"}
        resdir "resources/"]
    (println "Creating installation(aerobio home) directory")
    (fs/mkdirs aerobiodir)
    (println "Marking installation directory as aerobio home")
    (spit (fs/join aerobiodir ".aerobio-home-dir") "Home directory of aerobio")
    (println "Installing resources...")
    (fs/mkdir (fs/join aerobiodir "pipes"))
    (fs/mkdir (fs/join aerobiodir "cache"))
    (doseq [res ["services" "bin" #_"bam.aerobio.io" #_"vcf.aerobio.io"]]
      (doseq [[path uris] (cp/resources (io/resource res))
              :let [uri (first uris)
                    relative-path (subs path 1)
                    output-dir (->> relative-path
                                    fs/dirname
                                    (fs/join
                                     aerobiodir
                                     (if (resmap res) (str resdir res) res))
                                    fs/fullpath)
                    output-file (->> relative-path
                                     (fs/join
                                      aerobiodir
                                      (if (resmap res) (str resdir res) res))
                                     fs/fullpath io/file)]]
        (when (not (re-find #"^pack/" path)) ; bug in cp/resources regexer
          (println :PATH path)
          (println :RELATIVE-PATH relative-path)
          (when (not (fs/exists? output-dir))
            (fs/mkdirs output-dir))
          (println uri :->> output-file)
          (with-open [in (io/input-stream uri)]
            (io/copy in output-file))
          (when (= res "bin")
            (.setExecutable output-file true false)))))
    (println "\n\n*** Installation complete")))


(defn- find-set-home-dir
  "Tries to find the aerobio home directory and, if not current working
   directory sets working directory to home directory.
  "
  []
  (let [aerobioev "AEROBIO_HOME"
        evdir (->  aerobioev getenv fs/fullpath)
        curdir (fs/pwd)
        stdhm (fs/fullpath "~/.aerobio")]
    (cond
     (and (getenv aerobioev) (not= evdir curdir)
          (->> ".aerobio-home-dir" (fs/join evdir) fs/exists?))
     (do (fs/cd evdir) evdir)

     (->> ".aerobio-home-dir" (fs/join curdir) fs/exists?) curdir

     (and (fs/exists? stdhm) (not= stdhm curdir)
          (->> ".aerobio-home-dir" (fs/join stdhm) fs/exists?))
     (do (fs/cd stdhm) stdhm)

     :else nil)))


(defn- set-configuration []
  (let [m (->> "config.clj" (fs/join (fs/pwd))
               slurp edn/read-string)]
    (swap! pams/params (fn[_] m))
    (bpams/set-configuration (pams/get-params :biodb-info))
    m))

(defn- setup-timbre []
  "timbre-pre-v2 settings"
  (timbre/set-config! [:shared-appender-config :spit-filename]
                      "/home/jsa/Clojure/Projects/aerobio/main-log.txt")
  (timbre/set-config! [:appenders :standard-out   :enabled?] false)
  (timbre/set-config! [:appenders :spit           :enabled?] true))

(defn- run-server
  "Run the aerobio server on port PORT. Change clj-aerobio-port in
   bam.aerobio.io if it is not the same as port. This ensures user can
   start server on different ports w/o needing to edit JS files.
  "
  [port]
  (set-configuration)
  (pg/init-pgms)
  (setup-timbre)
  (svr/start! port))


(defn -main
  "Self installation and web server"
  [& args]

  (let [opts (parse-opts args cli-options)
        options (opts :options)
        arguments (opts :arguments)
        summary (opts :summary)
        errors (opts :erros)
        http-port (options :port)
        rpl-port (options :repl-port)
        aerobio-dir (options :install)
        nrepl-handler (apply nrs/default-handler
                             (concat (map resolve cider-middleware)
                                     [#'wrap-refactor]))]
    (if errors
      (println "Error(s): " errors)
      (cond
        (and http-port rpl-port)
        (if (find-set-home-dir)
          (do
            (run-server http-port)
            nrs/start-server :port rpl-port :handler nrepl-handler)
          (do (println "aerobio server must run in home directory")
              (System/exit 1)))

        aerobio-dir
        (let [aerobiodir (get-install-dir)]
          (if (fs/exists? aerobiodir)
            (do (println "Selected install dir already exists!")
                (System/exit 1))
            (install-aerobio aerobiodir)))

        :else
        (do (println "Unknown options " options)
            (System/exit 1))))))


(comment
  [clojure.core.async.pool-size
   (System/setProperty
    "clojure.core.async.pool-size"
    (-> (Runtime/getRuntime)
        (.availableProcessors)
        (* 2) (+ 42) str))]

  :timbre-pre-v2
  (timbre/set-config! [:shared-appender-config :spit-filename]
                      "/home/jsa/Clojure/Projects/aerobio/main-log.txt")
  (timbre/set-config! [:appenders :standard-out   :enabled?] false)
  (timbre/set-config! [:appenders :spit           :enabled?] true)
  )
