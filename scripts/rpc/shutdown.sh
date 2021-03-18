#!/bin/bash

(echo '{"method":"POST","query":"SHUTDOWN"}') | curl -s --data-binary @- localhost:8334

