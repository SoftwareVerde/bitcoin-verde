package com.softwareverde.bitcoin.secp256k1;

import com.softwareverde.bitcoin.jni.NativeSecp256k1;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.cryptography.secp256k1.key.PublicKey;
import com.softwareverde.cryptography.secp256k1.signature.Signature;
import com.softwareverde.logging.Logger;

public class Secp256k1 extends com.softwareverde.cryptography.secp256k1.Secp256k1 {

    protected static Boolean _verifySignatureViaJni(final Signature signature, final PublicKey publicKey, final ByteArray message) {
        try {
            final Signature canonicalSignature = signature.asCanonical();
            final ByteArray canonicalSignatureBytes = canonicalSignature.encode();
            return NativeSecp256k1.verifySignature(message, canonicalSignatureBytes, publicKey);
        }
        catch (final Exception exception) {
            Logger.warn(exception);
            return false;
        }
    }

    public static Boolean verifySignature(final Signature signature, final PublicKey publicKey, final byte[] message) {
        if (NativeSecp256k1.isEnabled()) {
            return _verifySignatureViaJni(signature, publicKey, ByteArray.wrap(message));
        }

        // Fallback to BouncyCastle if the libsecp256k1 failed to load for this architecture...
        return _verifySignatureViaBouncyCastle(signature, publicKey, message);
    }

    public static Boolean verifySignature(final Signature signature, final PublicKey publicKey, final ByteArray message) {
        if (NativeSecp256k1.isEnabled()) {
            return _verifySignatureViaJni(signature, publicKey, message);
        }

        // Fallback to BouncyCastle if the libsecp256k1 failed to load for this architecture...
        return _verifySignatureViaBouncyCastle(signature, publicKey, message.getBytes());
    }

    protected Secp256k1() { }
}
