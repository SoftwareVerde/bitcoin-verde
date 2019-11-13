#!/bin/bash

KEYFILE="$1"
MESSAGE="$2"

if [ "$#" -ne 1 ]; then
    echo -n "Key File: "
    read KEYFILE

    echo -n "Message: "
    MESSAGE=$(</dev/stdin)

    echo
fi

cd out
./run-signature.sh "../${KEYFILE}" "${MESSAGE}"

