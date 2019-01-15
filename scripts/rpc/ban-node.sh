#!/bin/bash

echo -n "HOST: "
read HOST

(echo "{\"method\":\"POST\",\"query\":\"BAN_NODE\",\"parameters\":{\"host\":\"${HOST}\"}}" && sleep 2) | nc localhost 8334

