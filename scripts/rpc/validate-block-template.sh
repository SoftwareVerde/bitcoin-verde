#!/bin/bash

echo -n "BLOCK_DATA: "
read BLOCK_DATA

(echo "{\"method\":\"POST\",\"query\":\"VALIDATE_PROTOTYPE_BLOCK\",\"parameters\":{\"blockData\":\"${BLOCK_DATA}\"}}") | curl -s --data-binary @- localhost:8334

