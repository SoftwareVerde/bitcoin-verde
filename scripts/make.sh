#!/bin/bash

# Copy Config
mkdir -p out/conf
copy_config=1
if [ "$(ls -A out/conf)" ]; then
    echo -n "Overwrite existing configuration? [Y/n] "
    read confirm

    if [[ "${confirm}" == "n" ]]; then
        copy_config=0
    fi
fi
if [ "${copy_config}" -gt 0 ]; then
    cp -R conf/* out/conf/.
fi

# Make Java Binaries
./scripts/make-jar.sh

# Copy Daemon Scripts
mkdir -p out/daemons
cp -R daemons/* out/daemons/.

# Copy README
cp README.md out/.

# Create SSL Placeholder Directory
mkdir -p out/ssl

# Copy Web/HTML Files
./scripts/copy-www-data.sh

# Create Database Directories
mkdir -p out/data
mkdir -p out/tmp
mkdir -p out/logs

# Create Run-Scripts
./scripts/make-scripts.sh

