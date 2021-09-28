#!/bin/bash

echo -n "TRANSACTION HASH: "
read TRANSACTION_HASH

(echo "{\"method\":\"GET\",\"query\":\"IS_VALID_SLP_TRANSACTION\",\"parameters\":{\"hash\":\"${TRANSACTION_HASH}\"}}") | curl -s --http0.9 --data-binary @- localhost:8334

