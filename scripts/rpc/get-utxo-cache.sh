#!/bin/bash

(echo '{"method":"GET","query":"UTXO_CACHE"}') | curl -s --data-binary @- localhost:8334

