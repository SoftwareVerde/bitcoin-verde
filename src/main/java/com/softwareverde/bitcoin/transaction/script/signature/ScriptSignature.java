package com.softwareverde.bitcoin.transaction.script.signature;

import com.softwareverde.bitcoin.secp256k1.signature.Signature;
import com.softwareverde.bitcoin.transaction.script.signature.hashtype.HashType;
import com.softwareverde.constable.bytearray.ByteArray;

public class ScriptSignature {
    public static ScriptSignature fromBytes(final ByteArray bytes) {
        final Signature ecdsaSignature = Signature.fromBytes(bytes);
        if (ecdsaSignature == null) { return null; }

        final byte hashTypeByte = bytes.getByte(bytes.getByteCount() - 1);
        final HashType hashType = HashType.fromByte(hashTypeByte);

        return new ScriptSignature(ecdsaSignature, hashType);
    }

    protected final HashType _hashType;
    protected final Signature _signature;

    public ScriptSignature(final Signature signature, final HashType hashType) {
        _signature = signature;
        _hashType = hashType;
    }

    public HashType getHashType() {
        return _hashType;
    }

    public Signature getSignature() {
        return _signature;
    }
}
