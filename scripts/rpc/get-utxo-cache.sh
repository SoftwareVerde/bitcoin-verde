#!/bin/bash

(echo '{"method":"GET","query":"UTXO_CACHE"}') | curl --data-binary @- localhost:8334

