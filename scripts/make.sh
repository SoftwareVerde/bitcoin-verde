#!/bin/bash

./scripts/clean.sh

rm -rf out 2>/dev/null

./scripts/make-jar.sh

# Copy Config
mkdir -p out/conf
cp -R conf/* out/conf/.

# Copy README
cp README.md out/.

# Create SSL Placeholder Directory
mkdir -p out/ssl

./scripts/copy-www-data.sh

# Create Database Directories
mkdir -p out/data
mkdir -p out/tmp
mkdir -p out/logs

# Create Run-Scripts
./scripts/make-scripts.sh

