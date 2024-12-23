;; Copyright © Manetu, Inc.  All rights reserved

(ns manetu.sparql-loadtest.driver.drivers.gql
  (:require [org.httpkit.client :as http]
            [promesa.core :as p]
            [taoensso.timbre :as log]
            [cheshire.core :as json]
            [graphql-query.core :refer [graphql-query]]
            [manetu.sparql-loadtest.driver.api :as api]))

(defn http-post
  [url args]
  (log/debug "http request:" url args)
  (p/create
   (fn [resolve reject]
     (http/post url args
                (fn [{:keys [error] :as r}]
                  (if error
                    (reject error)
                    (try
                      (resolve (update r :body json/parse-string))
                      (catch Throwable t
                        (reject (ex-info "body is not json" {:response r}))))))))))

(defn ->query
  [query]
  (json/generate-string {:query (graphql-query query)}))

(defn ->bindings
  [bindings]
  (map (fn [[k v]] {:name (str "?" k) :value v}) bindings))

(def ->index (memoize (fn [i] (keyword (str "index" i)))))

(defn -sparql-query
  [{:keys [url insecure token] :as ctx} query batch-bindings]
  (log/trace "GQL: sparql-query->" query batch-bindings)
  (-> (http-post url
                 {:insecure? insecure
                  :basic-auth ["" token]
                  :headers {"content-type" "application/json"
                            "accepts" "application/json"}
                  :body (->query {:queries (map-indexed (fn [i bindings]
                                                          {:query/data [:sparql_query {:sparql_expr query
                                                                                       :encoding :URL
                                                                                       :bindings (->bindings bindings)}
                                                                        [:name :value]]
                                                           :query/alias (->index i)})
                                                        batch-bindings)})})
      (p/then (fn [{{:strs [data errors]} :body :as r}]
                (log/trace "result:" r "errors:" errors)
                (log/trace "data:" data)
                (if (some? errors)
                  (p/rejected (ex-info "graphql error" r))
                  (let [rows (->> data vals (map (fn [x] {:rows (count x)})))]
                    (log/trace "rows:" rows)
                    rows))))))

(defrecord GraphQLDriver [ctx]
  api/LoadDriver
  (sparql-query [_ query bindings]
    (-sparql-query ctx query bindings)))

(defn create
  [ctx]
  (GraphQLDriver. (update ctx :url #(str % "/graphql"))))
