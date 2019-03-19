(defproject aerial/aerobio "2.0.0"
  :description "A full DAG pgm graph multi tool server for dynamic bio pipeline"
  :url "https://github.com/aerial/aerobio"
  :license {:name "The MIT License (MIT)"
            :url  "http://opensource.org/licenses/MIT"
            :distribution :repo}
  :min-lein-version "2.3.3"
  :global-vars {*warn-on-reflection* false
                *assert* true}

  :dependencies
  [[org.clojure/clojure       "1.9.0"]
   [org.clojure/tools.reader  "1.0.0-beta3"]
   [org.clojure/tools.nrepl   "0.2.13"] ; Explicit nREPL
   [org.clojure/tools.cli     "0.3.3"]  ; cmd line arg processing
   [org.clojure/data.json     "0.2.6"]
   [org.clojure/core.async    "0.4.474"]

   [ring/ring-defaults "0.3.1"]
   [bk/ring-gzip "0.2.1"]
   [ring-cljsjs "0.1.0"]

   [com.rpl/specter "1.1.1"]

   [aerial.hanasu             "0.2.1"] ; websockets

   [aerial.fs                 "1.1.5"]
   [aerial.utils              "1.2.0"]
   [aerial.bio.utils          "2.0.0"]
   [net.apribase/clj-dns      "0.1.0"] ; reverse-dns-lookup bug

   [hiccup                    "1.0.5"] ; Optional, just for HTML
   [com.draines/postal        "2.0.1"] ; mail messaging
   [com.taoensso/timbre       "3.3.1" :exclusions [org.clojure/tools.reader]]
   [clojure-watch             "0.1.13"] ; watch dir for changes
   [cpath-clj                 "0.1.2"] ; Installation JAR resources access

   [me.raynes/conch           "0.8.0" :exclusions [org.clojure/tools.reader]]
   [prismatic/schema          "1.0.1"] ; data shape checks for pgm graphs
   [expound                   "0.7.2"] ; Human optimized msgs for spec
   [spec-provider            "0.4.14"] ; infer specs from sample data
   [phrase               "0.3-alpha4"] ; Actual end user msgs for spec?
   ]

  :plugins [[cider/cider-nrepl "0.17.0"]
            [refactor-nrepl    "2.4.0-SNAPSHOT"]]

  :profiles {:uberjar {:aot :all}}
  :main aerobio.core
  ;;:aot :all

  ;; Call `lein start-dev` to get a (headless) development repl that you can
  ;; connect to with Cider+emacs or your IDE of choice:
  :aliases
  {"start-dev"  ["repl" ":headless"]}

  :repositories
  {"lclrepo" "file:lclrepo"
   "sonatype-oss-public" "https://oss.sonatype.org/content/groups/public/"})
