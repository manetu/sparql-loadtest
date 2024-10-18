;; Copyright © Manetu, Inc.  All rights reserved

(ns manetu.sparql-loadtest.core
  (:require [manetu.sparql-loadtest.time :as t]
            [medley.core :as m]
            [promesa.core :as p]
            [taoensso.timbre :as log]
            [clojure.core.async :refer [<! go go-loop] :as async]
            [progrock.core :as pr]
            [doric.core :refer [table]]
            [ring.util.codec :as ring.codec]
            [manetu.sparql-loadtest.binding-loader :as binding-loader]
            [manetu.sparql-loadtest.driver.api :as driver.api]
            [manetu.sparql-loadtest.stats :as stats]))

(defn execute-query
  [{:keys [driver] :as ctx} query bindings]
  (log/trace "bindings:" bindings)
  (let [start (t/now)]
    @(-> (driver.api/sparql-query driver query bindings)
         (p/then
          (fn [result]
            {:success true :result result}))
         (p/catch
          (fn [e]
            (log/trace (str "ERROR:" (ex-message e) (ex-data e)))
            {:success false :exception e}))
         (p/then
          (fn [result]
            (let [end (t/now)
                  d (t/duration end start)]
              (log/trace "processed in" d "msecs")
              (assoc result
                     :duration d)))))))

(defn execute-queries
  [{:keys [concurrency query bindings nr] :as ctx} output-ch]
  (let [query (-> query slurp ring.codec/url-encode)
        input-ch (binding-loader/get-bindings bindings nr)]
    (p/create
     (fn [resolve reject]
       (go
         (log/trace "launching with concurrency:" concurrency)
         (<! (async/pipeline-blocking concurrency
                                      output-ch
                                      (map (partial execute-query ctx query))
                                      input-ch))
         (resolve true))))))

(defn show-progress
  [{:keys [progress concurrency] :as ctx} n mux]
  (when progress
    (let [ch (async/chan (* 4 concurrency))]
      (async/tap mux ch)
      (p/create
       (fn [resolve reject]
         (go-loop [bar (pr/progress-bar n)]
           (if (= (:progress bar) (:total bar))
             (do (pr/print (pr/done bar))
                 (resolve true))
             (do (<! ch)
                 (pr/print bar)
                 (recur (pr/tick bar))))))))))

(defn transduce-promise
  [{:keys [concurrency] :as ctx} n mux xform f]
  (p/create
   (fn [resolve reject]
     (go
       (let [ch (async/chan (* 4 concurrency))]
         (async/tap mux ch)
         (let [result (<! (async/transduce xform f (f) ch))]
           (resolve result)))))))

(defn round2
  "Round a double to the given precision (number of significant digits)"
  [precision ^double d]
  (let [factor (Math/pow 10 precision)]
    (/ (Math/round (* d factor)) factor)))

(defn successful?
  [{:keys [success]}]
  (true? success))

(defn failed?
  [{:keys [success]}]
  (false? success))

(defn rows-pred?
  [pred {{:keys [rows]} :result :as x}]
  (log/debug "x:" x)
  (pred rows))

(def zero-rows? (partial rows-pred? zero?))
(def some-rows? (partial rows-pred? pos?))

(def stat-preds
  [{:description "Errors"    :pred failed?}
   {:description "Not found" :pred (every-pred successful? zero-rows?)}
   {:description "Successes" :pred (every-pred successful? some-rows?)}
   {:description "Total"     :pred identity}])

(defn compute-summary-stats
  [options n mux {:keys [description pred]}]
  (-> (transduce-promise options n mux (comp (filter pred) (map :duration)) stats/summary)
      (p/then (fn [{:keys [dist] :as summary}]
                (-> summary
                    (dissoc :dist)
                    (merge dist)
                    (as-> $ (m/map-vals #(round2 2 (or % 0)) $))
                    (assoc :description description))))))

(defn compute-stats
  [ctx n mux]
  (-> (p/all (conj (map (partial compute-summary-stats ctx n mux) stat-preds)))
      (p/then (fn [summaries]
                (log/trace "summaries:" summaries)
                (let [failures (get-in summaries [0 :count])]
                  {:failures failures :summaries summaries})))))

(defn render
  [{:keys [nr] :as ctx} {:keys [total-duration summaries] :as stats}]
  (println (table [:description :count :min :mean :stddev :p50 :p90 :p99 :max :rate] (map #(update % :count (fn [count] (str (int count) " (" (* (/ count nr) 100) "%)"))) summaries)))
  (println "Total Duration:" (str total-duration "msecs")))

(defn process
  [{:keys [concurrency nr] :as ctx}]
  (let [output-ch (async/chan (* 4 concurrency))
        mux (async/mult output-ch)]
    (log/info "processing" nr "requests with concurrency" concurrency)
    @(-> (p/all [(t/now)
                 (execute-queries ctx output-ch)
                 (show-progress ctx nr mux)
                 (compute-stats ctx nr mux)])
         (p/then
          (fn [[start _ _ stats]]
            (let [end (t/now)
                  d (t/duration end start)]
              (-> stats
                  (update :summaries (fn [summaries]
                                       (map (fn [{:keys [count] :as summary}]
                                              (assoc summary :rate (round2 2 (* (/ count d) 1000))))
                                            summaries)))
                  (assoc :total-duration d)))))
         (p/then (fn [{:keys [failures] :as stats}]
                   (render ctx stats)
                   (if (pos? failures)
                     -1
                     0))))))
