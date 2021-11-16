#!/bin/bash

(echo '{"method":"POST","query":"COMMIT_UTXO_CACHE"}') | curl -s --http0.9 --data-binary @- localhost:8334

