#!/bin/bash

HEIGHT=0

rm -f out.dat

while [[ $HEIGHT -le 635259 ]] ; do
    (echo "{\"method\":\"GET\",\"query\":\"BLOCK_HEADER\",\"parameters\":{\"blockHeight\":\"${HEIGHT}\",\"rawFormat\":\"1\"}}" && sleep 0.1) | curl -s --data-binary @- localhost:8334 | sed -n 's/^.*block...\([0-9A-F]*\).*$/\1/p' | xxd -r -p >> out.dat
    let HEIGHT=$HEIGHT+1
    echo -n '.'
done
echo
