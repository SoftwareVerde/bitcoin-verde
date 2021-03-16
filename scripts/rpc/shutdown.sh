#!/bin/bash

(echo '{"method":"POST","query":"SHUTDOWN"}') | curl --data-binary @- localhost:8334

