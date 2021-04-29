#!/bin/bash

(echo '{"method":"POST","query":"CLEAR_TRANSACTION_INDEXES"}') | curl -s --data-binary @- localhost:8334

