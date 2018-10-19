package com.softwareverde.bitcoin.jni;

public class NativeUnspentTransactionOutputCache {
    public static native void _cacheUnspentTransactionOutputId(final byte[] transactionHash, final int transactionOutputIndex, final long transactionOutputId);
}
