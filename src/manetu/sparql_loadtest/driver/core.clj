;; Copyright © Manetu, Inc.  All rights reserved

(ns manetu.sparql-loadtest.driver.core
  (:require [manetu.sparql-loadtest.driver.drivers.gql :as gql]
            [manetu.sparql-loadtest.driver.drivers.null :as null]))

(def driver-map
  {:gql gql/create
   :null null/create})

(defn init [{:keys [driver] :as options}]
  (if-let [c (get driver-map driver)]
    (assoc options :driver (c options))
    (throw (ex-info "unknown driver" {:type driver}))))
