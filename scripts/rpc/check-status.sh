#!/bin/bash

(echo '{"method":"GET","query":"STATUS"}' && sleep 2) | nc localhost 8081

