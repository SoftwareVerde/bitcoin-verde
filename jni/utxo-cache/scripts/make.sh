#!/bin/bash

if [[ -z "${JAVA_HOME}" ]]; then
    echo "JAVA_HOME is not set."
    exit 1
fi

unameOut="$(uname -s)"
case "${unameOut}" in
    Linux*)     machine=Linux;;
    Darwin*)    machine=Mac;;
    CYGWIN*)    machine=Cygwin;;
    MINGW*)     machine=MinGw;;
    *)          machine="UNKNOWN:${unameOut}"
esac
echo ${machine}

if [ "${machine}" == "Linux" ]; then
    g++ -O3 -funroll-loops -fPIC -I"$JAVA_HOME/include" -I"$JAVA_HOME/include/linux" -shared -o utxocache.so com_softwareverde_bitcoin_jni_NativeUnspentTransactionOutputCache.cpp
elif [ "${machine}" == "Mac" ]; then
    g++ -O3 -funroll-loops -I"$JAVA_HOME/include" -I"$JAVA_HOME/include/darwin" -dynamiclib -o utxocache.dylib com_softwareverde_bitcoin_jni_NativeUnspentTransactionOutputCache.cpp
else
    # http://releases.llvm.org/download.html
    cl='clang++'
    "${cl}" -Xclang -flto-visibility-public-std -O3 -funroll-loops -I"$JAVA_HOME/include" -I"$JAVA_HOME/include/win32" -c -o com_softwareverde_bitcoin_jni_NativeUnspentTransactionOutputCache.o com_softwareverde_bitcoin_jni_NativeUnspentTransactionOutputCache.cpp
    "${cl}" -shared -o utxocache.dll com_softwareverde_bitcoin_jni_NativeUnspentTransactionOutputCache.o
    rm utxocache.lib utxocache.exp com_softwareverde_bitcoin_jni_NativeUnspentTransactionOutputCache.o
fi

