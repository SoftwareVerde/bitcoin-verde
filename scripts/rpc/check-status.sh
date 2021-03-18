#!/bin/bash

(echo '{"method":"GET","query":"STATUS"}') | curl -s --data-binary @- localhost:8334

