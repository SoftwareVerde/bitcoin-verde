package org.bitcoin;

import java.nio.ByteBuffer;

public class NativeSecp256k1 {
    public static native void secp256k1_destroy_context(long context);
    public static native int secp256k1_ecdsa_verify(ByteBuffer byteBuff, long context, int sigLen, int pubLen);
}
