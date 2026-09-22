(defproject aerial/aerobio "3.0.0"
  :description "A full DAG pgm graph multi tool server for dynamic bio pipeline"
  :url "https://github.com/aerial/aerobio"
  :license {:name "The MIT License (MIT)"
            :url  "http://opensource.org/licenses/MIT"
            :distribution :repo}
  :min-lein-version "2.3.3"
  :global-vars {*warn-on-reflection* false
                *assert* true}

  :dependencies
  [[org.clojure/clojure       "1.12.3"]
   [org.ow2.asm/asm           "7.1"]      ; tech.v3 req???
   [org.clojure/tools.reader  "1.4.0"]
   [nrepl                     "1.3.0"]   ; Explicit nREPL
   [org.clojure/tools.cli     "1.0.206"]  ; cmd line arg processing
   [org.clojure/data.json     "2.4.0"]
   [org.clojure/core.async    "1.4.627"
    :exclusions [org.ow2.asm/asm-all]]

   [hiccup                    "1.0.5"] ; Optional, just for HTML
   [com.draines/postal        "2.0.1"] ; mail messaging
   [org.slf4j/slf4j-nop      "1.7.36"] ; stop slf4j whinning
   [com.taoensso/truss       "1.11.0"]
   [com.taoensso/timbre       "6.5.0"] ; newer timbre stop warnings
   
   [clojure-watch             "0.1.13"] ; watch dir for changes
   [cpath-clj                 "0.1.2"] ; Installation JAR resources access

   [me.raynes/conch           "0.8.0" :exclusions [org.clojure/tools.reader]]
   [prismatic/schema          "1.4.1"] ; data shape checks for pgm graphs
   [expound                   "0.7.2"] ; Human optimized msgs for spec
   [spec-provider            "0.4.14"] ; infer specs from sample data
   [phrase               "0.3-alpha4"] ; Actual end user msgs for spec?
   
   [org.apache.commons/commons-math3 "3.6.1"]
   ;;[org.bytedeco/openblas "0.3.31-1.5.13" :classifier "linux-x86_64"]
   [org.bytedeco/mkl "2025.3-1.5.13" :classifier "linux-x86_64-redist"]
   [org.bytedeco/dnnl-platform "3.11-1.5.13"]
   [uncomplicate/neanderthal "0.61.0"]
   [uncomplicate/deep-diamond "0.43.0"]

   [techascent/tech.ml.dataset "7.067"]
   [scicloj/tablecloth "7.062"]
   [mysql/mysql-connector-java "8.0.33"]
   [seancorfield/next.jdbc "1.0.424"]
   [techascent/tech.ml.dataset.sql "7.029"]

   [scicloj/clojisr "1.0.0"]
   [cljam "0.8.5"]                     ; Exceptional BAM/SAM processing!

   [ring/ring-defaults "0.3.1"]
   [bk/ring-gzip "0.2.1"]
   [ring-cljsjs "0.1.0"]

   [com.rpl/specter "1.1.1"]

   [medley                    "1.4.0"] ; force to rid warning on abs
   [aerial.hanasu             "0.2.7"] ; websockets

   [aerial.fs                 "1.1.5"]
   [aerial.utils              "1.2.2"]
   [aerial.bio.utils          "2.2.2"]
   [net.apribase/clj-dns      "0.1.0"] ; reverse-dns-lookup bug

   ]

  :plugins [[cider/cider-nrepl "0.50.2"]
            #_[refactor-nrepl    "3.10.0"]]

  :profiles {:uberjar {:aot :all}}
  :main aerobio.core
  ;;:aot :all

  ;; Call `lein start-dev` to get a (headless) development repl that you can
  ;; connect to with Cider+emacs or your IDE of choice:
  :aliases
  {"start-dev"  ["repl" ":headless"]}

  :repositories
  {"maven-central-snapshots"
   "https://central.sonatype.com/repository/maven-snapshots"
   
   "lclrepo" "file:lclrepo"
   "sonatype-oss-public" "https://oss.sonatype.org/content/groups/public/"})
