#!/bin/bash

echo -n "HOST: "
read HOST

(echo "{\"method\":\"POST\",\"query\":\"UNBAN_NODE\",\"parameters\":{\"host\":\"${HOST}\"}}") | nc localhost 8334

