package com.softwareverde.bitcoin.transaction.script.signature;

import com.softwareverde.bitcoin.secp256k1.signature.SchnorrSignature;
import com.softwareverde.bitcoin.secp256k1.signature.Secp256k1Signature;
import com.softwareverde.bitcoin.secp256k1.signature.Signature;
import com.softwareverde.bitcoin.transaction.script.signature.hashtype.HashType;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayReader;
import com.softwareverde.constable.bytearray.ByteArray;

public class ScriptSignature {
    public static ScriptSignature fromBytes(final ByteArray bytes) {
        if (bytes == null) { return null; }

        final HashType hashType;
        final Signature signature;
        if (bytes.getByteCount() == SchnorrSignature.BYTE_COUNT) { // Schnorr CheckDataSignatures do not have a HashType...
            signature = SchnorrSignature.fromBytes(bytes);
            hashType = null;
        }
        else if (bytes.getByteCount() == (SchnorrSignature.BYTE_COUNT + 1)) {  // Schnorr Signature with HashType...
            signature = SchnorrSignature.fromBytes(bytes);
            final byte hashTypeByte = bytes.getByte(bytes.getByteCount() - 1);
            hashType = HashType.fromByte(hashTypeByte);
        }
        else { // Secp256k1 Signature with HashType...
            final ByteArrayReader byteArrayReader = new ByteArrayReader(bytes);
            signature = Secp256k1Signature.fromBytes(byteArrayReader);

            if (byteArrayReader.remainingByteCount() > 0) {
                final byte hashTypeByte = byteArrayReader.readByte();
                hashType = HashType.fromByte(hashTypeByte);
            }
            else {
                hashType = null;
            }
        }
        if (signature == null) { return null; }

        return new ScriptSignature(signature, hashType);
    }

    protected final HashType _hashType;
    protected final Signature _signature;

    public ScriptSignature(final Signature signature, final HashType hashType) {
        _signature = signature;
        _hashType = hashType;
    }

    /**
     * Returns the HashType, if provided.
     *  HashType may be null for CheckDataSignature signatures.
     */
    public HashType getHashType() {
        return _hashType;
    }

    public Signature getSignature() {
        return _signature;
    }
}
