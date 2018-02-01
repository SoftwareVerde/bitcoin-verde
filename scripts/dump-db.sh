#!/bin/bash

password=`cat .password 2>/dev/null`

mysqldump -u root -p${password} bitcoin --no-create-info > src/main/resources/queries/bitcoin-data.sql
mysqldump -u root -p${password} bitcoin --no-data > src/main/resources/queries/bitcoin.sql

