#!/bin/bash

(echo '{"method":"GET","query":"STATUS"}') | curl -s --http0.9 --data-binary @- localhost:8334

