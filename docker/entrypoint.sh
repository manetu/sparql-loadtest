#!/bin/bash

set -eux -o pipefail

exec java -jar /usr/local/app.jar -u $MANETU_URL -l $LOG_LEVEL --no-progress $LOADTEST_EXTRA_OPTIONS --concurrency $LOADTEST_CONCURRENCY --nr $LOADTEST_NR --query $LOADTEST_QUERY --bindings $LOADTEST_BINDINGS
