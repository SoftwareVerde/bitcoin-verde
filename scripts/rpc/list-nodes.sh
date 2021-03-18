#!/bin/bash

(echo '{"method":"GET","query":"NODES"}') | curl -s --data-binary @- localhost:8334

