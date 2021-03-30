#!/bin/bash

echo -n "Hash: "
read HASH

if [[ ! -z "${HASH}" ]]; then
    (echo "{\"method\":\"GET\",\"query\":\"DOUBLE_SPEND_PROOF\",\"parameters\":{\"hash\":\"${HASH}\"}}") | curl -s --data-binary @- localhost:8334
    exit
fi

echo -n "Transaction Hash: "
read TRANSACTION_HASH

if [[ ! -z "${TRANSACTION_HASH}" ]]; then
    (echo "{\"method\":\"GET\",\"query\":\"DOUBLE_SPEND_PROOF\",\"parameters\":{\"transactionHash\":\"${TRANSACTION_HASH}\"}}") | curl -s --data-binary @- localhost:8334
    exit
fi

echo -n "Previous Output Transaction Hash: "
read TRANSACTION_HASH
echo -n "Previous Output Index: "
read OUTPUT_INDEX

TRANSACTION_OUTPUT_IDENTIFIER="${TRANSACTION_HASH}:${OUTPUT_INDEX}"

(echo "{\"method\":\"GET\",\"query\":\"DOUBLE_SPEND_PROOF\",\"parameters\":{\"transactionOutputIdentifier\":\"${TRANSACTION_OUTPUT_IDENTIFIER}\"}}") | curl -s --data-binary @- localhost:8334

