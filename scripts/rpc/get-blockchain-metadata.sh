#!/bin/bash

(echo "{\"method\":\"GET\",\"query\":\"BLOCKCHAIN\",\"parameters\":{}}") | curl -s --data-binary @- localhost:8334

