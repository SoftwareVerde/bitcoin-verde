#!/bin/bash

(echo "{\"method\":\"GET\",\"query\":\"BLOCKCHAIN\",\"parameters\":{}}") | curl -s --http0.9 --data-binary @- localhost:8334

