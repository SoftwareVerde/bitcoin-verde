#!/bin/bash

cd ../../src/main/java/com/softwareverde/bitcoin/jni
javac -h . NativeUnspentTransactionOutputCache.java
rm NativeUnspentTransactionOutputCache.class
cd -

mv ../../src/main/java/com/softwareverde/bitcoin/jni/com_softwareverde_bitcoin_jni_NativeUnspentTransactionOutputCache.h .

