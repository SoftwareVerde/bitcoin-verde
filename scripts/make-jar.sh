#!/bin/bash

rm -rf out/bin 2>/dev/null
mkdir -p out/bin

./gradlew makeJar copyDependencies && cp $(ls -r build/libs/*.jar | head -1) out/bin/main.jar && cp -R build/libs/libs out/bin/. && chmod 770 out/bin/main.jar

