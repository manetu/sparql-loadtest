;; Copyright © Manetu, Inc.  All rights reserved

(ns manetu.sparql-loadtest.core
  (:require [manetu.sparql-loadtest.time :as t]
            [medley.core :as m]
            [promesa.core :as p]
            [taoensso.timbre :as log]
            [clojure.core.async :refer [<! go go-loop] :as async]
            [progrock.core :as pr]
            [kixi.stats.core :as kixi]
            [doric.core :refer [table]]
            [ring.util.codec :as ring.codec]
            [manetu.sparql-loadtest.binding-loader :as binding-loader]
            [manetu.sparql-loadtest.driver.api :as driver.api]))

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

(defn compute-summary-stats
  [options n mux]
  (transduce-promise options n mux (map :duration) kixi/summary))

(defn successful?
  [{:keys [success]}]
  (true? success))

(defn failed?
  [{:keys [success]}]
  (false? success))

(defn count-msgs
  [ctx n mux pred]
  (transduce-promise ctx n mux (filter pred) kixi/count))

(defn compute-stats
  [ctx n mux]
  (-> (p/all [(compute-summary-stats ctx n mux)
              (count-msgs ctx n mux successful?)
              (count-msgs ctx n mux failed?)])
      (p/then (fn [[summary s f]] (assoc summary :successes s :failures f)))))

(defn round2
  "Round a double to the given precision (number of significant digits)"
  [precision ^double d]
  (let [factor (Math/pow 10 precision)]
    (/ (Math/round (* d factor)) factor)))

(defn render
  [ctx {:keys [failures] :as stats}]
  (let [stats (m/map-vals (partial round2 2) stats)]
    (println (table [:successes :failures :min :q1 :median :q3 :max :total-duration :rate] [stats]))
    (if (pos? failures)
      -1
      0)))

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
          (fn [[start _ _ {:keys [successes] :as stats}]]
            (let [end (t/now)
                  d (t/duration end start)]
              (assoc stats :total-duration d :rate (* (/ successes d) 1000)))))
         (p/then (partial render ctx)))))
