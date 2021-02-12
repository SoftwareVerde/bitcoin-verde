#!/bin/bash

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"

# Returns the approximate synchronized block count within the out directory.
function getBlockCount() {
    cd "${SCRIPT_DIR}" >/dev/null
    project_root=$(cd ../../ && pwd)
    echo $(($(ls -l "${project_root}/out/data/network/blocks" | wc -l) * 2016))
}

