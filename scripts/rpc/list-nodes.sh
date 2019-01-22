#!/bin/bash

(echo '{"method":"GET","query":"NODES"}' && sleep 2) | nc localhost 8334

