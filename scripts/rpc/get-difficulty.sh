#!/bin/bash

(echo '{"method":"GET","query":"DIFFICULTY"}') | curl --data-binary @- localhost:8334

