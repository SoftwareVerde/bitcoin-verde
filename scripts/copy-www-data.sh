#!/bin/bash

# Copy Explorer Static Web Data
rm -rf out/explorer/www
mkdir -p out/explorer/www
cp -R explorer/www/* out/explorer/www/.
cp -R www-shared/* out/explorer/www/.

# Copy Stratum Static Web Data
rm -rf out/stratum/www
mkdir -p out/stratum/www
cp -R stratum/www/* out/stratum/www/.
cp -R www-shared/* out/stratum/www/.

