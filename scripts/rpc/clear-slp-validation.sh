#!/bin/bash

(echo '{"method":"POST","query":"CLEAR_SLP_VALIDATION"}') | curl -s --data-binary @- localhost:8334

