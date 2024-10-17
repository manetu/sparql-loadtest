(defproject manetu/sparql-loadtest "0.0.1-SNAPSHOT"
  :description "A utility to measure SPARQL query performance on the Manetu Platform"
  :plugins [[lein-cljfmt "0.9.2"]
            [lein-kibit "0.1.8"]
            [lein-bikeshed "0.5.2"]
            [lein-cloverage "1.2.4"]
            [jonase/eastwood "1.4.0"]
            [lein-bin "0.3.5"]]
  :dependencies [[org.clojure/clojure "1.12.0"]
                 [org.clojure/core.async "1.6.681"]
                 [org.clojure/tools.cli "1.1.230"]
                 [com.taoensso/timbre "6.5.0"]
                 [com.fzakaria/slf4j-timbre "0.4.1"]
                 [org.slf4j/jul-to-slf4j "2.0.16"]
                 [org.slf4j/jcl-over-slf4j "2.0.16"]
                 [org.slf4j/log4j-over-slf4j "2.0.16"]
                 [ring/ring-codec "1.2.0"]
                 [funcool/promesa "11.0.678"]
                 [cheshire "5.13.0"]
                 [progrock "0.1.2"]
                 [doric "0.9.0"]
                 [org.clojure/data.csv "1.1.0"]
                 [kixi/stats "0.5.6"]
                 [medley "1.4.0"]
                 [district0x/graphql-query "1.0.6"]
                 [http-kit "2.7.0-RC1"]
                 [environ "1.2.0"]]
  :main ^:skip-aot manetu.sparql-loadtest.main
  :target-path "target/%s"
  :uberjar-name "app.jar"
  :jvm-opts ["-server"]

  :bin {:name "manetu-sparql-loadtest"
        :bin-path "target"
        :bootclasspath false}

  :eastwood {:add-linters [:unused-namespaces]
             :exclude-linters [:deprecations :suspicious-expression :local-shadows-var :unused-meta-on-macro :reflection]}

  ;; nREPL by default starts in the :main namespace, we want to start in `user`
  ;; because that's where our development helper functions like (refresh) live.
  :repl-options {:init-ns user}

  :profiles {:dev {:dependencies [[org.clojure/tools.namespace "1.5.0"]]}
             :uberjar {:aot :all}})
