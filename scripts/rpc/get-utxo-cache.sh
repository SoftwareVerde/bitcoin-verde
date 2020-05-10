#!/bin/bash

(echo '{"method":"GET","query":"UTXO_CACHE"}') | nc localhost 8334

