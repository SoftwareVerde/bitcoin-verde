#!/bin/bash

dot=$(which dot)
if [ -z "${dot}" ]; then
    echo "WARNING: 'graphviz' not installed."
    exit 1
fi

./gradlew makeDocumentation
cp -R build/docs/javadoc explorer/www/documentation/.

