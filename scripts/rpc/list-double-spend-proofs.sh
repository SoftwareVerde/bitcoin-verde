#!/bin/bash

(echo "{\"method\":\"GET\",\"query\":\"DOUBLE_SPEND_PROOFS\",\"parameters\":{}}") | curl -s --http0.9 --data-binary @- localhost:8334

