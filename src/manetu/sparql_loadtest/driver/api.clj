;; Copyright © Manetu, Inc.  All rights reserved

(ns manetu.sparql-loadtest.driver.api)

(defprotocol LoadDriver
  (sparql-query [this query bindings]))
