#!/bin/bash

echo -n "Address: "
read ADDRESS

if [[ ! -z "${ADDRESS}" ]]; then
    (echo "{\"method\":\"GET\",\"query\":\"BALANCE\",\"parameters\":{\"address\":\"${ADDRESS}\"}}") | curl -s --http0.9 --data-binary @- localhost:8334
    exit
fi

echo -n "Script Hash: "
read SCRIPT_HASH

(echo "{\"method\":\"GET\",\"query\":\"BALANCE\",\"parameters\":{\"scriptHash\":\"${SCRIPT_HASH}\"}}") | curl -s --http0.9 --data-binary @- localhost:8334

