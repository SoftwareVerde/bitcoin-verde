#!/bin/bash

(echo '{"method":"GET","query":"DIFFICULTY"}') | curl -s --data-binary @- localhost:8334

