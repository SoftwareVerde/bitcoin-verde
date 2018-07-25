#!/bin/bash

echo -n "Address: "
read ADDRESS

(echo "{\"method\":\"GET\",\"query\":\"BALANCE\",\"parameters\":{\"address\":\"${ADDRESS}\"}}" && sleep 2) | nc localhost 8334

