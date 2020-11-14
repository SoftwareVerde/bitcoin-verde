#!/bin/bash

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
cd "${SCRIPT_DIR}"

system_gmp_dir='/usr/local/opt/gmp'
system_dir="${system_gmp_dir}/lib"
lib_dir='lib'
filename='libgmp.10.dylib'

cat << EOF
This script installs GMP, which is necessary for verifying secp256k1 signatures
via the optimized libsecp256k1 native library. Neither of these libraries are
required to run Bitcoin Verde but are more performant than the default.

WARNING: This script copies a pre-built library for OSX called libgmp.
GMP is the GNU Multiple Precision Arithmetic Library. libgmp is used by
Bitcoin Verde within the native Secp256k1 implementation, "libsecp256k1" which
is an optional performance optimization. You do not need to run this script to
run Bitcoin Verde.

If you have homebrew then consider using that instead of this script.

To install libgmp via homebrew, execute:
    brew install gmp

EOF

echo -n 'Continue? [y/N] '
read confirm

if [[ "${confirm}" != 'y' && "${confirm}" != 'Y' ]]; then
    echo 'Abort.'
    exit 1
fi

if [[ -f "${system_dir}/${filename}" ]]; then
    echo "Aborting. GMP is already installed at ${system_dir}/${filename}."
    exit 1
fi

echo -e "Exec: sudo mkdir -p \"${system_dir}\""
sudo mkdir -p "${system_dir}"

echo -e "Exec: sudo cp \"${lib_dir}/${filename}\" \"${system_dir}/.\""
sudo cp "${lib_dir}/${filename}" "${system_dir}/."

echo -e "Exec: sudo chmod -R 755 \"${system_gmp_dir}\""
sudo chmod -R 755 "${system_gmp_dir}"

echo -e "Exec: sudo chown -R root:wheel \"${system_gmp_dir}\""
sudo chown -R root:wheel "${system_gmp_dir}"

