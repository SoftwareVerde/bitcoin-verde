#!/bin/bash

rm -rf out/bin 2>/dev/null
mkdir -p out/bin

# Delete old dependencies
rm -f build/libs/libs/*

./gradlew makeJar copyDependencies && cp $(ls -r build/libs/*.jar | head -1) out/bin/main.jar && cp -R build/libs/libs out/bin/. && chmod 770 out/bin/main.jar
success=$?

if [[ ! "${success}" ]]; then
    exit 1
fi

cp libs/* out/bin/libs/. 2>/dev/null

exit 0
