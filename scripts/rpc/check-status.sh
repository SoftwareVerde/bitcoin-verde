#!/bin/bash

(echo '{"method":"GET","query":"STATUS"}') | curl --data-binary @- localhost:8334

