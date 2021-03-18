#!/bin/bash

echo -n "Address: "
read ADDRESS

(echo "{\"method\":\"GET\",\"query\":\"BALANCE\",\"parameters\":{\"address\":\"${ADDRESS}\"}}") | curl -s --data-binary @- localhost:8334

