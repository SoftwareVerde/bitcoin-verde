#!/bin/bash

echo -n "HOST: "
read HOST

(echo "{\"method\":\"POST\",\"query\":\"BAN_NODE\",\"parameters\":{\"host\":\"${HOST}\"}}") | nc localhost 8334

