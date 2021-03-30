#!/bin/bash

(echo "{\"method\":\"GET\",\"query\":\"DOUBLE_SPEND_PROOFS\",\"parameters\":{}}") | curl -s --data-binary @- localhost:8334

