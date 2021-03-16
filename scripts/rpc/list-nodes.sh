#!/bin/bash

(echo '{"method":"GET","query":"NODES"}') | curl --data-binary @- localhost:8334

