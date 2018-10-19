#!/bin/bash

cd ../../src/main/java/com/softwareverde/bitcoin/jni/bin
javac -h . NativeUnspentTransactionOutputCache.java
rm NativeUnspentTransactionOutputCache.class
cd -

mv ../../src/main/java/com/softwareverde/bitcoin/jni/bin/com_softwareverde_bitcoin_jni_bin_NativeUnspentTransactionOutputCache.h .

