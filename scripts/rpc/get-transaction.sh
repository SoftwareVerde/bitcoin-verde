#!/bin/bash

echo -n "RAW FORMAT: [y/N] "
read RAW_FORMAT

if [[ "${RAW_FORMAT}" == "y" || "${RAW_FORMAT}" == "Y" ]]; then
    RAW_FORMAT=1
else
    RAW_FORMAT=0
fi

echo -n "HASH: "
read HASH

(echo "{\"method\":\"GET\",\"query\":\"TRANSACTION\",\"parameters\":{\"hash\":\"${HASH}\",\"rawFormat\":\"${RAW_FORMAT}\"}}") | curl -s --data-binary @- localhost:8334

