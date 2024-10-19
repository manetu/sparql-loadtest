FROM ubuntu:24.10

WORKDIR /src

RUN \
  set -eux \
  && apt-get update \
  && apt-get install -y --no-install-suggests \
        openjdk-22-jdk \
        make \
        wget

COPY project.clj .
COPY lein .
RUN ./lein deps

COPY . .
RUN make bin

ENV MANETU_URL="https://ingress.manetu-platform"
ENV LOG_LEVEL="info"
ENV LOADTEST_CONCURRENCY="64"
ENV LOADTEST_NR="10000"
ENV LOADTEST_QUERY="/etc/manetu/loadtest/examples/label-by-email.sparql"
ENV LOADTEST_BINDINGS="/etc/manetu/loadtest/examples/bindings.csv"
ENV LOADTEST_EXTRA_OPTIONS="--insecure"

COPY target/uberjar/app.j* /usr/local/
COPY docker/entrypoint.sh /usr/local/bin
COPY examples/by-email/* /etc/manetu/loadtest/examples/

ENTRYPOINT ["entrypoint.sh"]
