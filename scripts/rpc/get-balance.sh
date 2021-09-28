#!/bin/bash

echo -n "Address: "
read ADDRESS

(echo "{\"method\":\"GET\",\"query\":\"BALANCE\",\"parameters\":{\"address\":\"${ADDRESS}\"}}") | curl -s --http0.9 --data-binary @- localhost:8334

