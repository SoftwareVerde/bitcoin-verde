#!/bin/bash

if [[ -z "${JAVA_HOME}" ]]; then
    echo "JAVA_HOME is not set."
    exit 1
fi

g++ -O3 -funroll-loops -I"$JAVA_HOME/include" -I"$JAVA_HOME/include/darwin" -dynamiclib -o utxocache.dylib com_softwareverde_bitcoin_jni_NativeUnspentTransactionOutputCache.cpp

