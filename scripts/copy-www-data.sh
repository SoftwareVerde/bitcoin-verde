#!/bin/bash

# Copy Explorer Static Web Data
rm -rf out/explorer/www
mkdir -p out/explorer/www
cp -R explorer/www/* out/explorer/www/.
cp -R www-shared/* out/explorer/www/.

# Copy Wallet Static Web Data
rm -rf out/wallet/www
mkdir -p out/wallet/www
cp -R wallet/www/* out/wallet/www/.
cp -R www-shared/* out/wallet/www/.

