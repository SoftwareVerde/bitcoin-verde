#!/bin/bash

echo -n "BLOCK DATA: "
read BLOCK_DATA

# BLOCK_DATA=$(cat block.dat)

(echo "{\"method\":\"POST\",\"query\":\"BLOCK\",\"parameters\":{\"blockData\":\"${BLOCK_DATA}\"}}") | curl -s --data-binary @- localhost:8334

