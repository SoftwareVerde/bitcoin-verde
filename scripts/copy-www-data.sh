#!/bin/bash

# Copy Static Web Data
rm -rf out/www
mkdir -p out/www
cp -R www/* out/www/.

