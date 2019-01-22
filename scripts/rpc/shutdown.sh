#!/bin/bash

(echo '{"method":"POST","query":"SHUTDOWN"}' && sleep 2) | nc localhost 8334

