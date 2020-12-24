#!/bin/bash

if [ ! -x /usr/local/bin/brew ]; then
    >&2 echo "This script requires homebrew (https://brew.sh/)."
    exit 1
fi

# based on https://gist.github.com/souzagab/0ae60e61939d51385de87904b65b2da2

if [ -f /usr/local/opt/openssl/lib/libssl.1.0.0.dylib ]; then
    >&2 echo "OpenSSL 1.0 is already installed."
    exit 1
fi

brew install \
    https://raw.githubusercontent.com/Homebrew/homebrew-core/64555220bfbf4a25598523c2e4d3a232560eaad7/Formula/openssl.rb \
    -f

