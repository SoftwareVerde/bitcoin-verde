#!/bin/bash

(echo '{"method":"POST","query":"COMMIT_UTXO_CACHE"}') | nc localhost 8334

