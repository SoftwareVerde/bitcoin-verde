#!/bin/bash

(echo '{"method":"GET","query":"BLOCK_HEIGHT"}') | nc localhost 8334

