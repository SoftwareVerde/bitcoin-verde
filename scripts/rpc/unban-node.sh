#!/bin/bash

echo -n "HOST: "
read HOST

(echo "{\"method\":\"POST\",\"query\":\"UNBAN_NODE\",\"parameters\":{\"host\":\"${HOST}\"}}" && sleep 2) | nc localhost 8334

