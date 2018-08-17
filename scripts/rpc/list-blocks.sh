#!/bin/bash

echo -n "Block Height: "
read BLOCK_HEIGHT

echo -n "Block Count: "
read MAX_BLOCK_COUNT

(echo "{\"method\":\"GET\",\"query\":\"BLOCK_HEADERS\",\"parameters\":{\"blockHeight\":\"${BLOCK_HEIGHT}\",\"maxBlockCount\":\"${MAX_BLOCK_COUNT}\"}}" && sleep 2) | nc localhost 8334

