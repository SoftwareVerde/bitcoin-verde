#!/bin/bash

echo -n "BLOCK_DATA: "
read BLOCK_DATA

((echo "{\"method\":\"POST\",\"query\":\"VALIDATE_PROTOTYPE_BLOCK\",\"parameters\":{\"blockData\":\"${BLOCK_DATA}\"}}") && sleep 1) | nc localhost 8334

