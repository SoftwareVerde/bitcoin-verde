#!/bin/bash

echo -n "PAGE SIZE: "
read PAGE_SIZE 

echo -n "PAGE NUMBER: "
read PAGE_NUMBER

echo -n "HASH: "
read HASH

if [[ ! -z "${HASH}" ]]; then
    (echo "{\"method\":\"GET\",\"query\":\"BLOCK_TRANSACTIONS\",\"parameters\":{\"hash\":\"${HASH}\",\"pageSize\":\"${PAGE_SIZE}\",\"pageNumber\":\"${PAGE_NUMBER}\"}}") | curl -s --data-binary @- localhost:8334
    exit
fi

echo -n "HEIGHT: "
read HEIGHT

(echo "{\"method\":\"GET\",\"query\":\"BLOCK_TRANSACTIONS\",\"parameters\":{\"blockHeight\":\"${HEIGHT}\",\"pageSize\":\"${PAGE_SIZE}\"},\"pageNumber\":\"${PAGE_NUMBER}\"}}") | curl -s --data-binary @- localhost:8334

