#!/bin/bash

function count_zeroes {
    local block=$1

    count=0
    for (( i=0; i<${#block}; i=$((i+1)) )); do
        c="${block:$i:1}"
        if [[ "$c" == "0" ]]; then
            count=$((count+1))
        else
            echo -n $count
            return
        fi
    done

}

cd out/data

rm -rf index
rm -rf utxo

cd network

mkdir -p "pending-blocks"

if [[ -d blocks ]]; then
    cd blocks

    sections=$(ls)

    for s in $sections; do
        cd $s;
        blocks=$(ls)
        for block in $blocks; do
            zeroCount=$(count_zeroes $block)
            mkdir -p "../../pending-blocks/$zeroCount"
            mv $block ../../pending-blocks/$zeroCount/.
        done
        cd ..
    done
fi

cd ../../..
rm logs/*

