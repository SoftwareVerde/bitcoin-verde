#!/bin/bash

(echo '{"method":"GET","query":"BLOCK_HEIGHT"}') | curl --data-binary @- localhost:8334

