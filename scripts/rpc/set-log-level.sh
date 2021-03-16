#!/bin/bash

echo -n "PACKAGE: "
read PACKAGE

echo -n "LOG LEVEL: "
read LOG_LEVEL

(echo "{\"method\":\"POST\",\"query\":\"SET_LOG_LEVEL\",\"parameters\":{\"packageName\":\"${PACKAGE}\",\"logLevel\":\"${LOG_LEVEL}\"}}") | curl --data-binary @- localhost:8334

