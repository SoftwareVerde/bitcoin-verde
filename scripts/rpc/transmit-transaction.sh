#!/bin/bash

echo -n "TRANSACTION DATA: "
read TRANSACTION_DATA

(echo "{\"method\":\"POST\",\"query\":\"TRANSACTION\",\"parameters\":{\"transactionData\":\"${TRANSACTION_DATA}\"}}") | curl --data-binary @- localhost:8334

