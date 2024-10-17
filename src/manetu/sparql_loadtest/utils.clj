;; Copyright © Manetu, Inc.  All rights reserved

(ns manetu.sparql-loadtest.utils
  (:require [clojure.string :as string]))

(defn prep-usage [msg] (->> msg flatten (string/join \newline)))

(defn exit [status msg & args]
  (do
    (apply println msg args)
    status))

(defn version [] (str "manetu-sparql-loadtest version: v" (System/getProperty "sparql-loadtest.version")))
