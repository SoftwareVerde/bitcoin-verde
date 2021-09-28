#!/bin/bash

(echo '{"method":"GET","query":"NODES"}') | curl -s --http0.9 --data-binary @- localhost:8334

