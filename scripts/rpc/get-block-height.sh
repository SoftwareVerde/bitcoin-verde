#!/bin/bash

(echo '{"method":"GET","query":"BLOCK_HEIGHT"}' && sleep 1) | nc localhost 8334

