#!/bin/bash

echo -n "BLOCK_DATA: "
read BLOCK_DATA

(echo "{\"method\":\"POST\",\"query\":\"VALIDATE_PROTOTYPE_BLOCK\",\"parameters\":{\"blockData\":\"${BLOCK_DATA}\"}}") | curl -s --http0.9 --data-binary @- localhost:8334

