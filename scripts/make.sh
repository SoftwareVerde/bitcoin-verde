#!/bin/bash

./scripts/clean.sh

rm -rf out 2>/dev/null

./scripts/make-jar.sh

# Copy Config
mkdir -p out/conf
cp -R conf/* out/conf/.

# Copy Static Web Data
mkdir -p out/www
cp -R www/* out/www/.

# Create Database Directories
mkdir -p out/data
mkdir -p out/tmp

# Create Run-Scripts
./scripts/make-scripts.sh

