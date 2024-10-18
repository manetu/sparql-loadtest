#!/bin/bash

set -eux -o pipefail

exec java -jar /usr/local/app.jar -u $MANETU_URL --no-progress -l $LOG_LEVEL --concurrency $LOADTEST_CONCURRENCY --nr $LOADTEST_NR --query $LOADTEST_QUERY --bindings $LOADTEST_BINDINGS
