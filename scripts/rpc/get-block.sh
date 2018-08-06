#!/bin/bash

echo -n "HASH: "
read HASH

(echo "{\"method\":\"GET\",\"query\":\"BLOCK\",\"parameters\":{\"hash\":\"${HASH}\"}}" && sleep 30) | nc localhost 8334

