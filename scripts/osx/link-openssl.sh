#!/bin/bash

system_dir='/usr/lib'
alternate_dir='/usr/local/opt/openssl@1.0/lib' # if installed via: brew install rbenv/tap/openssl@1.0
openssl_dir='/usr/local/opt/openssl/lib'

cat << EOF
This script is intended to resolve an issue where MySQL does not start due to
openssl libraries not being installed at the location it expects.

This script requests root access to create symlinks at [${openssl_dir}]
to the existing libraries located at [${system_dir}].
EOF

echo -n 'Continue? [y/N] '
read confirm

if [[ "${confirm}" != 'y' && "${confirm}" != 'Y' ]]; then
    echo 'Abort.'
    exit 1
fi

if [[ ! -f "${system_dir}/libssl.dylib" || ! -f "${system_dir}/libcrypto.dylib" ]]; then
    if [[ -f "${alternate_dir}/libssl.dylib" || ! "${alternate_dir}/libcrypto.dylib" ]]; then
        echo "Found openssl at alternate directory: ${alternate_dir}"
        system_dir="${alternate_dir}"
    else
        echo "Cannot create symlink. Is openssl installed at ${system_dir}?"
        exit 1
    fi
fi

if [ ! -f "${openssl_dir}/libssl.1.0.0.dylib" ]; then 
    sudo ln -s "${system_dir}/libssl.dylib" "${openssl_dir}/libssl.1.0.0.dylib" || exit 1
    echo -e "ln -s \"${system_dir}/libssl.dylib\" \"${openssl_dir}/libssl.1.0.0.dylib\""
fi

if [ ! -f "${openssl_dir}/libcrypto.1.0.0.dylib" ]; then 
    sudo ln -s "${system_dir}/libcrypto.dylib" "${openssl_dir}/libcrypto.1.0.0.dylib" || exit 1
    echo -e "ln -s \"${system_dir}/libcrypto.dylib\" \"${openssl_dir}/libcrypto.1.0.0.dylib\""
fi

