#!/bin/bash

rm -rf out/bin 2>/dev/null
mkdir -p out/bin

# Delete old dependencies
rm -rf build/libs/*

./gradlew makeLibJar copyDependencies && echo $(ls -r build/libs/*.jar | head -1)
exit 0

