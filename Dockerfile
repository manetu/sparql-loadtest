FROM ubuntu:24.10

WORKDIR /src

RUN \
  set -eux \
  && apt-get update \
  && apt-get install -y --no-install-suggests \
        openjdk-22-jdk \
        make \
        wget

COPY . .
RUN ls -la && make all

ENV MANETU_URL="ingress.manetu-platform"
ENV LOG_LEVEL="info"
ENV LOADTEST_CONCURRENCY="64"
ENV LOADTEST_NR="10000"
ENV LOADTEST_QUERY=/etc/manetu/loadtest/examples/label-by-email.sparql
ENV LOADTEST_BINDINGS=/etc/manetu/loadtest/examples/bindings.csv

COPY target/uberjar/app.j* /usr/local/
COPY docker/entrypoint.sh /usr/local/bin
COPY examples/by-email/* /etc/manetu/loadtest/examples/

ENTRYPOINT ["entrypoint.sh"]
