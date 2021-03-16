#!/bin/bash

echo -n "HOST: "
read HOST

(echo "{\"method\":\"POST\",\"query\":\"BAN_NODE\",\"parameters\":{\"host\":\"${HOST}\"}}") | curl --data-binary @- localhost:8334

