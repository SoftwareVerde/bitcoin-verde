#!/bin/bash

(echo "{\"method\":\"GET\",\"query\":\"BLOCKCHAIN\",\"parameters\":{}}" && sleep 30) | nc localhost 8334

