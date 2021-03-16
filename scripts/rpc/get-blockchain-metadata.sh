#!/bin/bash

(echo "{\"method\":\"GET\",\"query\":\"BLOCKCHAIN\",\"parameters\":{}}") | curl --data-binary @- localhost:8334

