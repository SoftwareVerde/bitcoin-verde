#!/bin/bash

echo -n "HASH: "
read HASH

(echo "{\"method\":\"POST\",\"query\":\"RECONSIDER_BLOCK\",\"parameters\":{\"blockHash\":\"${HASH}\"}}") | curl -s --http0.9 --data-binary @- localhost:8334

