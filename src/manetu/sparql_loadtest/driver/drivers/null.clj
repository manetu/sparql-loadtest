;; Copyright © Manetu, Inc.  All rights reserved

(ns manetu.sparql-loadtest.driver.drivers.null
  (:require [taoensso.timbre :as log]
            [promesa.core :as p]
            [manetu.sparql-loadtest.driver.api :as api]))

(defn -sparql-query [query bindings]
  (log/trace "NULL: sparql-query->" query bindings)
  (p/resolved {}))

(defrecord NullDriver []
  api/LoadDriver
  (sparql-query [_ query bindings]
    (-sparql-query query bindings)))

(defn create
  [ctx]
  (NullDriver.))
