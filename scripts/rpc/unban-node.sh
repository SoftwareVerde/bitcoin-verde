#!/bin/bash

echo -n "HOST: "
read HOST

(echo "{\"method\":\"POST\",\"query\":\"UNBAN_NODE\",\"parameters\":{\"host\":\"${HOST}\"}}") | curl -s --http0.9 --data-binary @- localhost:8334

