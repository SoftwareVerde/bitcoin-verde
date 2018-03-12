#!/bin/bash

./scripts/clean.sh

rm -rf out 2>/dev/null

./scripts/make-jar.sh

# Copy Config
mkdir -p out/conf
cp -R conf/* out/conf/.

# Create Database Directories
mkdir -p out/data
mkdir -p out/tmp

# Create Run-Scripts
./scripts/make-scripts.sh

