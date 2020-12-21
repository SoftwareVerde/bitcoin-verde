#!/bin/bash

(echo '{"method":"GET","query":"NODES"}' && sleep 1) | nc localhost 8334

