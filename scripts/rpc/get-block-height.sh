#!/bin/bash

echo -n "HASH: "
read HASH

if [[ ! -z "${HASH}" ]]; then
    (echo "{\"method\":\"GET\",\"query\":\"BLOCK_HEIGHT\",\"parameters\":{\"hash\":\"${HASH}\"}}") | curl -s --http0.9 --data-binary @- localhost:8334
    exit
fi

echo -n "TRANSACTION_HASH: "
read TRANSACTION_HASH

if [[ ! -z "${TRANSACTION_HASH}" ]]; then
    (echo "{\"method\":\"GET\",\"query\":\"BLOCK_HEIGHT\",\"parameters\":{\"transactionHash\":\"${TRANSACTION_HASH}\"}}") | curl -s --http0.9 --data-binary @- localhost:8334
    exit
fi

(echo '{"method":"GET","query":"BLOCK_HEIGHT"}') | curl -s --http0.9 --data-binary @- localhost:8334

