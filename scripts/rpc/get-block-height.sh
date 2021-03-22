#!/bin/bash

(echo '{"method":"GET","query":"BLOCK_HEIGHT"}') | curl -s --data-binary @- localhost:8334

