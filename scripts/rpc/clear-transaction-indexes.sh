#!/bin/bash

(echo '{"method":"POST","query":"CLEAR_TRANSACTION_INDEXES"}') | curl -s --http0.9 --data-binary @- localhost:8334

