#!/bin/bash

files=$(find out/data/network/blocks -type f)
for file in ${files}; do
    if [[ ! $(file ${file} | grep compressed) ]]; then
        echo ${file}
        gzip -c ${file} > "${file}".gz
        mv "${file}" "${file}".swp
        mv "${file}".gz "${file}"
        rm "${file}".swp
    fi
done

