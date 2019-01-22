#!/bin/bash

echo -n "HASH: "
read HASH

if [[ ! -z "${HASH}" ]]; then
    (echo "{\"method\":\"GET\",\"query\":\"BLOCK\",\"parameters\":{\"hash\":\"${HASH}\"}}" && sleep 30) | nc localhost 8334
    exit
fi

echo -n "HEIGHT: "
read HEIGHT

(echo "{\"method\":\"GET\",\"query\":\"BLOCK\",\"parameters\":{\"blockHeight\":\"${HEIGHT}\"}}" && sleep 30) | nc localhost 8334

