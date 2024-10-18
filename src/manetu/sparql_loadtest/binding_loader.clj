;; Copyright © Manetu, Inc.  All rights reserved

(ns manetu.sparql-loadtest.binding-loader
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [clojure.core.async :as async]
            [taoensso.timbre :as log]))

(defn csv->maps [data]
  (map zipmap
       (repeat (first data)) ;; First row is the header
       (rest data)))

(defn parse-csv
  [rdr]
  (csv->maps (csv/read-csv rdr)))

(defn record-seq
  [rdr nr]
  (->> rdr parse-csv cycle (take nr)))

(defn csv->bindings
  "Given 'path' to a .csv file, return 'nr' records, possibly repeating if nr exceeds the record count in the file"
  [path nr]
  (log/info "Loading bindings from:" path)
  (let [ch (async/chan)]
    (async/thread
      (with-open [rdr (io/reader path)]
        (doseq [record (record-seq rdr nr)]
          (async/>!! ch record))
        (async/close! ch)))
    ch))

(defn null-bindings
  "Generates the required number of empty bindings, used when the caller does not specify --bindings with a .csv file"
  [nr]
  (log/debug "no bindings detected")
  (async/to-chan! (repeat nr {})))

(defn get-bindings
  [bindings nr]
  (if (some? bindings)
    (csv->bindings bindings nr)
    (null-bindings nr)))