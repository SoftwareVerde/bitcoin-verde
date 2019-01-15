#!/bin/bash

echo -n 'Key Destination: '
read key_file

echo -n 'CSR Destination: '
read csr_file

echo -n 'Self-Sign Cert Destination: '
read cert_file

openssl genrsa -out "${key_file}" 2048
openssl req -new -out "${csr_file}" -key "${key_file}"
openssl x509 -req -days 365 -in "${csr_file}" -signkey "${key_file}" -out "${cert_file}"

