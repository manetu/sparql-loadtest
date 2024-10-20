# sparql-loadtest

The sparql-loadtest is a command-line tool to measure SPARQL query performance on the Manetu platform.  The basic premise is that you may specify an arbitrary query with optional initial-bindings, and the tool will run a fixed number of iterations with the specified concurrency.  After the test, the tool will compute and report various metrics such as the min/ave/max latency and the throughput rate.

This utility helps measure your cluster's overall query performance and helps optimize your SPARQL query.

## Installing

### Prerequisites

- JDK (tested with JDK22)
- make

## Building

```
$ make
```

## Usage

```shell
$ java -jar ./target/uberjar/app.jar -h
Usage: manetu-sparql-loadtest [options]

Measures the performance metrics of concurrent SPARQL queries to the Manetu platform

Options:
  -h, --help
  -v, --version                 Print the version and exit
  -u, --url URL                 The connection URL
      --insecure                Disable TLS host checking
      --[no-]progress    true   Enable/disable progress output (default: enabled)
  -l, --log-level LEVEL  :info  Select the logging verbosity level from: [trace, debug, info, error]
  -c, --concurrency NUM  64     The number of parallel jobs to run
  -d, --driver DRIVER    :gql   Select the driver from: [null, gql]
  -q, --query PATH              The path to a file containing a SPARQL query to use in test
  -b, --bindings FILE           (Optional) The path to a CSV file to cycle through as input bindings to each SPARQL query
  -n, --nr COUNT         10000  The number of queries to execute
```

### Connection details

In addition to --url and optionally --insecure, you must set the environment variable MANETU_TOKEN to a personal access token issued from your Manetu cluster.

### Test parameters

The parameters of the test include the level of concurrency (--concurrency), the number of iterations (--nr), the query (--query) specified as a path to a file containing a SPARQL expression, and optionally a set of initial bindings (--bindings) defined as a path to a CSV file.

#### SPARQL expression

The SPARQL expression must be legal SPARQL grammar.  For example:

```sparql
PREFIX manetu: <http://manetu.com/manetu/>
PREFIX mmeta:  <http://manetu.io/rdf/metadata/0.1/>

SELECT ?label

WHERE  {
       ?s manetu:email ?email ;
          mmeta:vaultLabel ?label .
 }
```
The expression may optionally contain input bindings that will be satisfied by the CSV input file specified by --bindings.  The utility will submit the column header of the CSV file as a binding verbatim, with the requisite "?" prefix.  For example, for the following CSV:

```csv
id,name,email
1,alice,alice@example.com
2,bob,bob@example.com
```
This would generate bindings such as:

```json
{"?id": "1", "?name": "alice", "?email": "alice@example.com"}
```
The utility will cycle through the rows for cases where the number of iterations (--nr) exceeds the rows in the --bindings.  For example, if the initial bindings contains 4 rows (numbered 1-4) and the test is executed with --nr 10, the utility will generate queries with the rows cycled e.g. [1 2 3 4 1 2 3 4 1 2].

### Example

For this example, we will use a query to search the graph by email to return a vault label.  We will use several files from this repository for our [query](./examples/by-email/label-by-email.sparql) and [bindings](./examples/by-email/bindings.csv).  Running this example assumes you have onboarded some [mock-data](./examples/by-email/data-loader.csv) using the Manetu [data-loader](https://github.com/manetu/data-loader) utility.

```shell
$ java -jar ./target/uberjar/app.jar -u https://manetu.example.com --insecure -q ./examples/by-email/label-by-email.sparql --bindings ./examples/by-email/bindings.csv -n10000 -c64
```
If successful,  the test should display something similar to the following:

```shell
2024-10-18T19:54:24.398Z INFO processing 10000 requests with concurrency 64
2024-10-18T19:54:24.403Z INFO Loading bindings from: ./examples/by-email/bindings.csv
10000/10000   100% [==================================================]  ETA: 00:00
|-------------+----------------+-------+--------+--------+--------+--------+--------+--------+--------|
| Description |      Count     |  Min  |  Mean  | Stddev |   P50  |   P90  |   P99  |   Max  |  Rate  |
|-------------+----------------+-------+--------+--------+--------+--------+--------+--------+--------|
| Errors      | 0 (0.0%)       | 0.0   | 0.0    | 0.0    | 0.0    | 0.0    | 0.0    | 0.0    | 0.0    |
| Not found   | 0 (0.0%)       | 0.0   | 0.0    | 0.0    | 0.0    | 0.0    | 0.0    | 0.0    | 0.0    |
| Successes   | 10000 (100.0%) | 29.05 | 110.79 | 35.83  | 111.48 | 147.47 | 194.54 | 419.91 | 490.11 |
| Total       | 10000 (100.0%) | 29.05 | 110.79 | 35.83  | 111.48 | 147.47 | 194.54 | 419.91 | 490.11 |
|-------------+----------------+-------+--------+--------+--------+--------+--------+--------+--------|
Total Duration: 20403.423232msecs
```

## Running on Kubernetes

The following instructions allow you to deploy this tool into a Kubernetes instance.  You would typically use the Kubernetes option to run the tool in close proximity to a Manetu instance running in the same cluster, though this is not strictly required.

Manetu hosts a Docker-based version of this tool on Dockerhub:

[https://hub.docker.com/repository/docker/manetuops/sparql-loadtest/general](https://hub.docker.com/repository/docker/manetuops/sparql-loadtest/general)

We will use this to deploy into Kubernetes.

### Setup

#### Credentials

You must inject a Personal Access Token to your Manetu instance as a Kubernetes [Secret](https://kubernetes.io/docs/concepts/configuration/secret/) into your cluster for the tool to use, like so:

```shell
kubectl create secret generic manetu-sparql-loadtest --from-literal=MANETU_TOKEN=<your token>
```

#### Query/Bindings data

You must also deploy the query/bindings you wish to use as a Kubernetes [ConfigMap](https://kubernetes.io/docs/concepts/configuration/configmap/).  The ConfigMap should have two bindings named 'bindings.csv' and 'query.sparql', which we will use to inject the files into our deployment in the next step.

##### Example

``` shell
kubectl create configmap manetu-sparql-loadtest --from-file=bindings.csv=examples/by-uuid/bindings.csv --from-file=query.sparql=examples/by-uuid/query.sparql
```

### Launching the test

Next, we can define a Kubernetes [Job](https://kubernetes.io/docs/concepts/workloads/controllers/job/) that leverages our secret/configmap like so:

```yaml
apiVersion: batch/v1
kind: Job
metadata:
  name: manetu-sparql-loadtest
spec:
  template:
    spec:
      restartPolicy: Never
      containers:
      - name: sparql-loadtest
        image: manetuops/sparql-loadtest:latest
        imagePullPolicy: Always
        env:
          - name: MANETU_URL
            value: https://ingress.manetu-platform
          - name: LOG_LEVEL
            value: info
          - name: LOADTEST_CONCURRENCY
            value: "64"
          - name: LOADTEST_NR
            value: "10000"
          - name: LOADTEST_QUERY
            value: "/etc/manetu/loadtest/query.sparql"
          - name: LOADTEST_BINDINGS
            value: "/etc/manetu/loadtest/bindings.csv"
        envFrom:
          - secretRef:
              name: manetu-sparql-loadtest
        volumeMounts:
          - name: data
            mountPath: "/etc/manetu/loadtest"
            readOnly: true
      volumes:
      - name: data
        configMap:
          name: manetu-sparql-loadtest
```

For convenience, this file is available in this repository as [kubernetes/job.yaml](./kubernetes/job.yaml).  You may apply this like so:

```shell
kubectl apply -f kubernetes/job.yaml
```

### Obtaining results

Once the job is completed, you may use 'kubectl logs' to obtain the test results.  First, obtain the name of the pod, like so:

```shell
$ kubectl get pods
NAME                           READY   STATUS      RESTARTS   AGE
manetu-sparql-loadtest-4bmkh   0/1     Completed   0          28m
```

Then, query for the job logs, like so:

```shell
$ kubectl logs manetu-sparql-loadtest-4bmkh
+ exec java -jar /usr/local/app.jar -u https://ingress.manetu-platform -l info --no-progress --insecure --concurrency 64 --nr 10000 --query /etc/manetu/loadtest/examples/label-by-email.sparql --bindings /etc/manetu/loadtest/examples/bindings.csv
2024-10-18T23:51:00.921Z INFO processing 10000 requests with concurrency 64
2024-10-18T23:51:00.943Z INFO Loading bindings from: /etc/manetu/loadtest/examples/bindings.csv
|-------------+----------------+-------+--------+--------+--------+--------+--------+--------+--------|
| Description |      Count     |  Min  |  Mean  | Stddev |   P50  |   P90  |   P99  |   Max  |  Rate  |
|-------------+----------------+-------+--------+--------+--------+--------+--------+--------+--------|
| Errors      | 0 (0.0%)       | 0.0   | 0.0    | 0.0    | 0.0    | 0.0    | 0.0    | 0.0    | 0.0    |
| Not found   | 0 (0.0%)       | 0.0   | 0.0    | 0.0    | 0.0    | 0.0    | 0.0    | 0.0    | 0.0    |
| Successes   | 10000 (100.0%) | 26.37 | 109.17 | 42.2   | 109.07 | 144.32 | 186.83 | 586.19 | 489.78 |
| Total       | 10000 (100.0%) | 26.37 | 109.17 | 42.2   | 109.07 | 144.32 | 186.83 | 586.19 | 489.78 |
|-------------+----------------+-------+--------+--------+--------+--------+--------+--------+--------|
Total Duration: 20417.142784msecs
```

> Tip: If the job is experiencing errors, you may set LOG_LEVEL to 'trace' to diagnose the problem.
