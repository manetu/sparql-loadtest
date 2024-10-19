#!/bin/bash

invoke_graphql() {
    yq -r -o=json - | curl --data-binary @- --silent --show-error --fail $MANETU_EXTRA_CURL_ARGS --header 'Content-Type: application/json' -u ":$MANETU_TOKEN" $MANETU_URL/graphql
}

format_output () {
    cat - | jq ".data.sparql_query"
}

invoke_sparql_query() {
    local expr=$(cat - | url-encode)

    cat <<EOF | invoke_graphql | format_output
query: |
    {
      sparql_query(
        sparql_expr: "$expr",
        encoding: URL
      ) {
         name
         value
        }
    }
EOF
}

echo "uuid"

cat <<EOF | invoke_sparql_query |  jq ".[].[0].value" | sed 's/\"//g'
PREFIX rdf:    <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
PREFIX manetu: <http://manetu.com/manetu/>
PREFIX mmeta:  <http://manetu.io/rdf/metadata/0.1/>

SELECT ?uuid

WHERE  {
       ?m rdf:predicate manetu:email ;
          mmeta:uuid ?uuid .
}
LIMIT 1000
EOF
