#!/bin/bash

echo -n "Address: "
read ADDRESS

(echo "{\"method\":\"GET\",\"query\":\"BALANCE\",\"parameters\":{\"address\":\"${ADDRESS}\"}}") | nc localhost 8334

