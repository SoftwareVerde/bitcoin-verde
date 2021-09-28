#!/bin/bash

(echo '{"method":"GET","query":"BLOCK_HEIGHT"}') | curl -s --http0.9 --data-binary @- localhost:8334

