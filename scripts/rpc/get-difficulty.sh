#!/bin/bash

(echo '{"method":"GET","query":"DIFFICULTY"}') | curl -s --http0.9 --data-binary @- localhost:8334

