#!/bin/bash

(echo "{\"method\":\"GET\",\"query\":\"BLOCKCHAIN\",\"parameters\":{}}") | nc localhost 8334

