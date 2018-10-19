#!/bin/bash

if [[ -z "${JAVA_HOME}" ]]; then
    echo "JAVA_HOME is not set."
    exit 1
fi

g++ -I"$JAVA_HOME/include" -I"$JAVA_HOME/include/darwin" -dynamiclib -o utxocache.dylib com_softwareverde_bitcoin_jni_bin_NativeUnspentTransactionOutputCache.cpp

