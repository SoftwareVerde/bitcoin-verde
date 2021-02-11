#!/bin/bash

(echo '{"method":"POST","query":"SHUTDOWN"}' && sleep 1) | nc localhost 8334

