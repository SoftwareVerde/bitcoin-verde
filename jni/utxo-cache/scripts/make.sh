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
    x86_64-w64-mingw32-g++ -O3 -funroll-loops -I"%JAVA_HOME%\include" -I"%JAVA_HOME%\include\win32" -shared -o utxocache.dll com_softwareverde_bitcoin_jni_NativeUnspentTransactionOutputCache.cpp
fi

