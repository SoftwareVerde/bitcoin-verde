#!/bin/bash

echo -n "HOST: "
read HOST

echo -n "PORT: "
read PORT

(echo "{\"method\":\"POST\",\"query\":\"ADD_NODE\",\"parameters\":{\"host\":\"${HOST}\",\"port\":${PORT}}}") | curl -s --http0.9 --data-binary @- localhost:8334

