#!/bin/bash

echo -n "BLOCK DATA: "
read BLOCK_DATA

# BLOCK_DATA=$(cat block.dat)

(echo "{\"method\":\"POST\",\"query\":\"BLOCK\",\"parameters\":{\"blockData\":\"${BLOCK_DATA}\"}}") | nc localhost 8334

