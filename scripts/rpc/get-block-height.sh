#!/bin/bash

(echo '{"method":"GET","query":"BLOCK-HEIGHT"}' && sleep 2) | nc localhost 8334

