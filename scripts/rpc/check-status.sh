#!/bin/bash

(echo '{"method":"GET","query":"STATUS"}' && sleep 5) | nc localhost 8334

