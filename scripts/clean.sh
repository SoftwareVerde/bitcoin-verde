#!/bin/bash

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"

. "${SCRIPT_DIR}/lib/util.sh"

synced_block_count=$(getBlockCount)
if [[ "${synced_block_count}" -gt 50000 ]]; then
    echo -n "${synced_block_count} blocks will be deleted. Continue? [y/N] "
    read confirm
    if [[ "${confirm}" != "y" ]]; then
        exit 1
    fi

    echo "Deleting out directory..."
    sleep 3
fi

./gradlew clean

rm -rf build 2>/dev/null
rm -rf out 2>/dev/null

