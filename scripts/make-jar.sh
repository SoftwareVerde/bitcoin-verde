#!/bin/bash

rm -rf out/bin 2>/dev/null
mkdir -p out/bin

./gradlew makeJar && cp build/libs/verde-bitcoin-server*.jar out/bin/main.jar && chmod 770 out/bin/main.jar

