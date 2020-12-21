#!/bin/bash

RAW_FORMAT=1
HEIGHT=0

rm -f out.dat

while [[ $HEIGHT -lt 575000 ]] ; do
    (echo "{\"method\":\"GET\",\"query\":\"BLOCK_HEADER\",\"parameters\":{\"blockHeight\":\"${HEIGHT}\",\"rawFormat\":\"${RAW_FORMAT}\"}}") | nc localhost 8334 | sed 's/^.*block...\([0-9A-F]\+\).*$/\1/g' | xxd -r -p >> out.dat
    let HEIGHT=$HEIGHT+1
done
