#!/bin/bash

echo -n "RAW FORMAT: [y/N] "
read RAW_FORMAT

if [[ "${RAW_FORMAT}" == "y" || "${RAW_FORMAT}" == "Y" ]]; then
    RAW_FORMAT=1
else
    RAW_FORMAT=0
fi

echo -n "Block Height: "
read BLOCK_HEIGHT

echo -n "Block Count: "
read MAX_BLOCK_COUNT

(echo "{\"method\":\"GET\",\"query\":\"BLOCK_HEADERS\",\"parameters\":{\"blockHeight\":\"${BLOCK_HEIGHT}\",\"maxBlockCount\":\"${MAX_BLOCK_COUNT}\",\"rawFormat\":\"${RAW_FORMAT}\"}}") | curl -s --data-binary @- localhost:8334

