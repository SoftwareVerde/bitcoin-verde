#!/bin/bash

echo -n 'Signed Certificate File: '
read server_cert

echo -n 'Private Key File: '
read server_key

echo -n 'Destination Key File: '
read out_file

openssl pkcs12 -export -in "${server_cert}" -inkey "${server_key}" -out "${out_file}" -nodes -passout 'pass:'
echo "Wrote Server Key: ${out_file}"

