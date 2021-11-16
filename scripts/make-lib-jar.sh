#!/bin/bash

# Delete old dependencies
rm -rf build/libs/*

./gradlew makeLibJar copyDependencies && echo $(ls -r build/libs/*.jar | head -1)
exit 0

