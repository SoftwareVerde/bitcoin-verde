#!/bin/bash

echo -n "Address: "
read ADDRESS

(echo "{\"method\":\"GET\",\"query\":\"BALANCE\",\"parameters\":{\"address\":\"${ADDRESS}\"}}") | curl --data-binary @- localhost:8334

