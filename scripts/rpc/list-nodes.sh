#!/bin/bash

(echo '{"method":"GET","query":"NODES"}') | nc localhost 8334

