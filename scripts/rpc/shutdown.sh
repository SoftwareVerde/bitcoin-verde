#!/bin/bash

(echo '{"method":"POST","query":"SHUTDOWN"}') | curl -s --http0.9 --data-binary @- localhost:8334

