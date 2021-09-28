#!/bin/bash

echo -n "TRANSACTION DATA: "
read TRANSACTION_DATA

(echo "{\"method\":\"POST\",\"query\":\"TRANSACTION\",\"parameters\":{\"transactionData\":\"${TRANSACTION_DATA}\"}}") | curl -s --http0.9 --data-binary @- localhost:8334

