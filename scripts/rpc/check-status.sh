#!/bin/bash

(echo '{"method":"GET","query":"STATUS"}' && sleep 10) | nc localhost 8334

