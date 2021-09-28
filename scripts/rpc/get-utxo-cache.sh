#!/bin/bash

(echo '{"method":"GET","query":"UTXO_CACHE"}') | curl -s --http0.9 --data-binary @- localhost:8334

