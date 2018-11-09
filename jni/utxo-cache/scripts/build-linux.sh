#!/bin/bash
set -o xtrace

PORT=21
LINUX_MACHINE='24.35.60.138'

cd ..
scp -P ${PORT} -r utxo-cache ${LINUX_MACHINE}:.
ssh -p ${PORT} ${LINUX_MACHINE} -t 'bash -i -c "cd utxo-cache; ./scripts/make.sh; exit"'
cd -
scp -P ${PORT} ${LINUX_MACHINE}:utxo-cache/utxocache.so .

