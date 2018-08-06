#!/bin/bash

echo -n "HASH: "
read HASH

(echo "{\"method\":\"GET\",\"query\":\"TRANSACTION\",\"parameters\":{\"hash\":\"${HASH}\"}}" && sleep 10) | nc localhost 8334

