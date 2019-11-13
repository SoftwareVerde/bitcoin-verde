#!/bin/bash

echo -n "TRANSACTION HASH: "
read TRANSACTION_HASH

(echo "{\"method\":\"GET\",\"query\":\"IS_VALID_SLP_TRANSACTION\",\"parameters\":{\"hash\":\"${TRANSACTION_HASH}\"}}") | nc localhost 8334

