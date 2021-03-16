#!/bin/bash

(echo '{"method":"POST","query":"COMMIT_UTXO_CACHE"}') | curl --data-binary @- localhost:8334

