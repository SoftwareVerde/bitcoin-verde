#!/bin/bash

(echo '{"method":"GET","query":"BLOCK_HEIGHT"}' && sleep 2) | nc localhost 8334

