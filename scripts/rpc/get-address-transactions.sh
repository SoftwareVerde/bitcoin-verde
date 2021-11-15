#!/bin/bash

echo -n "RAW FORMAT: [y/N] "
read RAW_FORMAT

if [[ "${RAW_FORMAT}" == "y" || "${RAW_FORMAT}" == "Y" ]]; then
    RAW_FORMAT=1
else
    RAW_FORMAT=0
fi

echo -n "Address: "
read ADDRESS

if [[ ! -z "${ADDRESS}" ]]; then
    (echo "{\"method\":\"GET\",\"query\":\"ADDRESS\",\"parameters\":{\"address\":\"${ADDRESS}\",\"rawFormat\":\"${RAW_FORMAT}\"}}") | curl -s --http0.9 --data-binary @- localhost:8334
    exit
fi

echo -n "Script Hash: "
read SCRIPT_HASH

(echo "{\"method\":\"GET\",\"query\":\"ADDRESS\",\"parameters\":{\"scriptHash\":\"${SCRIPT_HASH}\",\"rawFormat\":\"${RAW_FORMAT}\"}}") | curl -s --http0.9 --data-binary @- localhost:8334

