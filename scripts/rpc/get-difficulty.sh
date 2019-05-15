#!/bin/bash

(echo '{"method":"GET","query":"DIFFICULTY"}' && sleep 2) | nc localhost 8334

