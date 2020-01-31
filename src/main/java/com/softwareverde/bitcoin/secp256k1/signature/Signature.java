package com.softwareverde.bitcoin.secp256k1.signature;

import com.softwareverde.constable.Const;
import com.softwareverde.constable.bytearray.ByteArray;

public interface Signature extends Const {
    enum Type {
        SCHNORR, SECP256K1, BITCOIN_MESSAGE
    }

    Type getType();

    ByteArray getR();
    ByteArray getS();
    ByteArray encode();
    Boolean isCanonical();
    Signature asCanonical();

    /**
     * Empty signatures are a special kind of signature for Script execution that are created by inflating from an empty byte array.
     */
    Boolean isEmpty();
}

abstract class SignatureCore implements Signature {
    @Override
    public String toString() {
        final ByteArray encoded = this.encode();
        return (encoded != null ? encoded.toString() : null);
    }
}
