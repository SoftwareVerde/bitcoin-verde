#!/bin/bash

(echo '{"method":"POST","query":"SHUTDOWN"}' && sleep 1) | nc 192.168.86.26 8334

