(defproject aerial/clj-iobio "0.9.1"
  :description "Clojure iobio full DAG pgm graph multi tool server"
  :url "https://github.com/aerial/clj-iobio"
  :license {:name "The MIT License (MIT)"
            :url  "http://opensource.org/licenses/MIT"
            :distribution :repo}
  :min-lein-version "2.3.3"
  :global-vars {*warn-on-reflection* false
                *assert* true}

  :dependencies
  [[org.clojure/clojure       "1.7.0"]
   [org.clojure/tools.nrepl  "0.2.12"] ; Explicit nREPL

   [org.clojure/core.async    "0.1.346.0-17112a-alpha"]
   ;;[org.clojure/core.async    "0.2.391"]
   [org.clojure/data.json     "0.2.6"]
   [org.clojure/tools.cli     "0.3.3"] ; cmd line arg processing

   [com.draines/postal        "2.0.1"] ; mail messaging
   ;;[org.clojure/java.jdbc "0.6.2-alpha3"] ; sql RDB access
   ;;[mysql/mysql-connector-java "6.0.4"]  ; mysql connector
   ;;[com.mchange/c3p0 "0.9.2.1"] ; connection pooling

   [aerial.fs                 "1.1.5"]
   [aerial.utils              "1.2.0"]
   [aerial.bio.utils          "1.1.2"]

   ;;[com.taoensso/sente        "1.4.1"] ; We use new msgpSente
   [com.taoensso/encore      "1.22.0" :exclusions [org.clojure/tools.reader]]
   [com.taoensso/timbre       "3.3.1"] ; Logger
   [me.raynes/conch           "0.8.0"] ; streaming shell
   [clojure-watch            "0.1.10"] ; watch dir for changes
   [cpath-clj                 "0.1.2"] ; Installation JAR resources access
   [prismatic/schema          "1.0.1"] ; data shape checks for pgm graphs

   [http-kit                 "2.1.19"] ; Evented web server w/webscockets

   [net.apribase/clj-dns      "0.1.0"] ; reverse-dns-lookup

   [compojure                 "1.3.4"] ; Or routing lib of your choice
   [ring                      "1.3.2"]
   ;; [ring-anti-forgery      "1.0.0"]
   [ring/ring-defaults        "0.1.3"] ; Includes `ring-anti-forgery`, etc.
   [hiccup                    "1.0.5"] ; Optional, just for HTML
   ;;
   ;;; Transit deps optional; may be used to aid perf. of larger data payloads
   ;;; (see reference example for details):
   [com.cognitect/transit-clj  "0.8.259"]
   [com.cognitect/transit-cljs "0.8.199"]]

  :plugins [[cider/cider-nrepl "0.12.0"]
            [refactor-nrepl    "2.2.0"]]

  :profiles {:uberjar {:aot :all}}
  :main iobio.core
  ;;:aot :all

  ;; Call `lein start-dev` to get a (headless) development repl that you can
  ;; connect to with Cider+emacs or your IDE of choice:
  :aliases
  {"start-dev"  ["repl" ":headless"]}

  :repositories
  {"lclrepo" "file:lclrepo"
   "sonatype-oss-public" "https://oss.sonatype.org/content/groups/public/"})
