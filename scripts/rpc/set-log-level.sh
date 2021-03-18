#!/bin/bash

echo -n "PACKAGE: "
read PACKAGE

echo -n "LOG LEVEL: "
read LOG_LEVEL

(echo "{\"method\":\"POST\",\"query\":\"SET_LOG_LEVEL\",\"parameters\":{\"packageName\":\"${PACKAGE}\",\"logLevel\":\"${LOG_LEVEL}\"}}") | curl -s --data-binary @- localhost:8334

