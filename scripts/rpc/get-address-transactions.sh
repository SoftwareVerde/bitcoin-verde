#!/bin/bash

echo -n "Address: "
read ADDRESS

(echo "{\"method\":\"GET\",\"query\":\"ADDRESS\",\"parameters\":{\"address\":\"${ADDRESS}\"}}" && sleep 2) | nc localhost 8334

