# sparql-loadtest

The sparql-loadtest is a command-line tool to measure SPARQL query performance on the Manetu platform.  The basic premise is that you may specify an arbitrary query with optional initial-bindings, and the tool will run a fixed number of iterations with the specified concurrency.  After the test, the tool will compute and report various metrics such as the min/ave/max latency and the throughput rate.

This utility helps measure your cluster's overall query performance and helps optimize your SPARQL query.

## Installing

### Prerequisites

- JDK (tested with JDK22)
- make

## Building

### Prerequisites

In addition to the requirements for installation, you will also need:

- Leiningen

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
2024-10-17T20:21:33.172Z INFO processing 10000 requests with concurrency 64
2024-10-17T20:21:33.175Z INFO Loading bindings from: ./examples/by-email/bindings.csv
10000/10000   100% [==================================================]  ETA: 00:00
|-----------+----------+------+-------+--------+--------+--------+----------------+--------|
| Successes | Failures |  Min |   Q1  | Median |   Q3   |   Max  | Total Duration |  Rate  |
|-----------+----------+------+-------+--------+--------+--------+----------------+--------|
| 10000.0   | 0.0      | 27.1 | 87.05 | 113.04 | 132.75 | 292.57 | 20517.48       | 487.39 |
|-----------+----------+------+-------+--------+--------+--------+----------------+--------|
```

