;; Copyright © Manetu, Inc.  All rights reserved

(ns manetu.sparql-loadtest.main
  (:require [clojure.tools.cli :refer [parse-opts]]
            [clojure.string :as string]
            [taoensso.timbre :as log]
            [environ.core :refer [env]]
            [manetu.sparql-loadtest.core :as core]
            [manetu.sparql-loadtest.driver.core :as driver.core]
            [manetu.sparql-loadtest.utils :refer [prep-usage exit version]])
  (:gen-class))

(defn set-logging
  [level]
  (log/set-config!
   {:level level
    :ns-whitelist  ["manetu.*"]
    :appenders
    {:custom
     {:enabled? true
      :async false
      :fn (fn [{:keys [timestamp_ msg_ level] :as data}]
            (println (force timestamp_) (string/upper-case (name level)) (force msg_)))}}}))

(def log-levels #{:trace :debug :info :error})
(defn print-loglevels []
  (str "[" (string/join ", " (map name log-levels)) "]"))
(def loglevel-description
  (str "Select the logging verbosity level from: " (print-loglevels)))

(def drivers (into #{} (keys driver.core/driver-map)))
(defn print-drivers []
  (str "[" (string/join ", " (map name drivers)) "]"))
(def driver-description
  (str "Select the driver from: " (print-drivers)))

(def options-spec
  [["-h" "--help"]
   ["-v" "--version" "Print the version and exit"]
   ["-u" "--url URL" "The connection URL"]
   [nil "--insecure" "Disable TLS host checking"]
   [nil "--[no-]progress" "Enable/disable progress output (default: enabled)"
    :default true]
   ["-l" "--log-level LEVEL" loglevel-description
    :default :info
    :parse-fn keyword
    :validate [log-levels (str "Must be one of " (print-loglevels))]]
   ["-c" "--concurrency NUM" "The number of parallel jobs to run"
    :default 64
    :parse-fn parse-long
    :validate [pos? "Must be a positive integer"]]
   [nil "--batch-size NUM" "The size of the batch request"
    :default 1
    :parse-fn parse-long
    :validate [pos? "Must be a positive integer"]]
   ["-d" "--driver DRIVER" driver-description
    :default :gql
    :parse-fn keyword
    :validate [drivers (str "Must be one of " (print-drivers))]]
   ["-q" "--query PATH" "The path to a file containing a SPARQL query to use in test"]
   ["-b" "--bindings FILE" "(Optional) The path to a CSV file to cycle through as input bindings to each SPARQL query"]
   ["-n" "--nr COUNT" "The number of queries to execute"
    :default 10000
    :parse-fn parse-long
    :validate [pos? "Must be a positive integer"]]])

(defn usage [options-summary]
  (prep-usage [(version)
               ""
               "Usage: manetu-sparql-loadtest [options]"
               ""
               "Measures the performance metrics of concurrent SPARQL queries to the Manetu platform"
               ""
               "Options:"
               options-summary]))

(defn -app
  [& args]
  (let [{{:keys [help url log-level query] :as global-options} :options
         :keys [errors summary]}
        (parse-opts args options-spec :in-order true)]
    (cond

      help
      (exit 0 (usage summary))

      (not= errors nil)
      (exit -1 "Error: " (string/join errors))

      (:version global-options)
      (exit 0 (version))

      (not (some? url))
      (exit -1 "ERROR: Must set --url")

      (not (some? query))
      (exit -1 "ERROR: Must set --query")

      (not (some? (env :manetu-token)))
      (exit -1 "ERROR: Must set MANETU_TOKEN environment value to a Manetu Personal Access Token")

      :else
      (let [options (driver.core/init (assoc global-options :token (env :manetu-token)))]
        (set-logging log-level)
        (try
          (core/process options)
          (catch Exception ex
            (exit -1 (str "ERROR: " (ex-message ex)))))))))

(defn -main
  [& args]
  (let [code (apply -app args)]
    (shutdown-agents)
    (System/exit code)))
