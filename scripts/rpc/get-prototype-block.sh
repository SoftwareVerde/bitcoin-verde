#!/bin/bash

echo -n "RAW FORMAT: [y/N] "
read RAW_FORMAT

if [[ "${RAW_FORMAT}" == "y" || "${RAW_FORMAT}" == "Y" ]]; then
    RAW_FORMAT=1
else
    RAW_FORMAT=0
fi

(echo "{\"method\":\"GET\",\"query\":\"PROTOTYPE_BLOCK\",\"parameters\":{\"rawFormat\":\"${RAW_FORMAT}\"}}" && sleep 5) | curl -s --data-binary @- localhost:8334

