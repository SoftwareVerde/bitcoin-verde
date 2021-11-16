#!/bin/bash

(echo '{"method":"POST","query":"CLEAR_SLP_VALIDATION"}') | curl -s --http0.9 --data-binary @- localhost:8334

