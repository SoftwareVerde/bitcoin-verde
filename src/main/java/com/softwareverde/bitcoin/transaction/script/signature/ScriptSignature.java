package com.softwareverde.bitcoin.transaction.script.signature;

import com.softwareverde.bitcoin.secp256k1.signature.EmptySignature;
import com.softwareverde.bitcoin.secp256k1.signature.SchnorrSignature;
import com.softwareverde.bitcoin.secp256k1.signature.Secp256k1Signature;
import com.softwareverde.bitcoin.secp256k1.signature.Signature;
import com.softwareverde.bitcoin.server.main.BitcoinConstants;
import com.softwareverde.bitcoin.transaction.script.signature.hashtype.HashType;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayReader;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.ImmutableByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;

public class ScriptSignature {
    /**
     * Returns a ScriptSignature (Secp25k1 Signature and HashType) from the provided bytes.
     *  The ScriptSignatureContext is used to determine the appropriate Schnorr signature byte count (64 bytes with 1 optional byte for HashType).
     *  i.e. CheckDataSignature does not provide a hashType.
     *  If the provided ByteArray is empty (but not null), then the signature is valid, but empty.
     */
    public static ScriptSignature fromBytes(final ByteArray bytes, final ScriptSignatureContext scriptSignatureContext) {
        if (bytes == null) { return null; }

        if (bytes.isEmpty()) {
            return new ScriptSignature(EmptySignature.SECP256K1, null);
        }

        final int schnorrScriptSignatureByteCount;
        { // Schnorr signatures are 64 bytes, and only use the hashType byte for regular checksig operations...
            if (scriptSignatureContext == ScriptSignatureContext.CHECK_DATA_SIGNATURE) {
                schnorrScriptSignatureByteCount = SchnorrSignature.BYTE_COUNT;
            }
            else {
                schnorrScriptSignatureByteCount = (SchnorrSignature.BYTE_COUNT + HashType.BYTE_COUNT);
            }
        }

        final HashType hashType;
        final Signature signature;
        final ByteArray extraBytes;
        if (bytes.getByteCount() == schnorrScriptSignatureByteCount) {
            if (scriptSignatureContext == ScriptSignatureContext.CHECK_DATA_SIGNATURE) { // Schnorr CheckDataSignatures do not have a HashType...
                signature = SchnorrSignature.fromBytes(bytes);
                hashType = null;
            }
            else { // Schnorr Signature with HashType...
                signature = SchnorrSignature.fromBytes(bytes);
                final byte hashTypeByte = bytes.getByte(SchnorrSignature.BYTE_COUNT);
                hashType = HashType.fromByte(hashTypeByte);
            }

            extraBytes = NO_EXTRA_BYTES;
        }
        else { // Secp256k1 Signature with HashType...
            final ByteArrayReader byteArrayReader = new ByteArrayReader(bytes);
            signature = Secp256k1Signature.fromBytes(byteArrayReader);

            final int signatureByteCount = (bytes.getByteCount() - byteArrayReader.remainingByteCount());
            // 64-byte (+1 hashType) signatures are not allowed within ECDSA signature contexts when Schnorr signatures are enabled...
            if (signatureByteCount == SchnorrSignature.BYTE_COUNT) { return null; }

            if (byteArrayReader.remainingByteCount() > 0) {
                final byte hashTypeByte = bytes.getByte(bytes.getByteCount() - 1); // The HashType is always the last byte of the signature, if it's available.
                hashType = HashType.fromByte(hashTypeByte);
            }
            else {
                hashType = null;
            }

            final int hashTypeByteCount = (hashType != null ? 1 : 0);
            final int extraByteCount = (byteArrayReader.remainingByteCount() - hashTypeByteCount);
            if (extraByteCount > 0) {
                // The "extra" bytes are bytes between the end of the signature, and the beginning of the HashType.
                //  These extra bytes are not currently used for anything other than to indicate the signature was not strictly encoded.
                extraBytes = MutableByteArray.wrap(byteArrayReader.readBytes(extraByteCount));
            }
            else {
                extraBytes = NO_EXTRA_BYTES;
            }
        }
        if (signature == null) { return null; }

        return new ScriptSignature(signature, hashType, extraBytes);
    }

    protected static final ByteArray NO_EXTRA_BYTES = new ImmutableByteArray(new byte[0]);

    protected final HashType _hashType;
    protected final Signature _signature;
    protected final ByteArray _extraBytes;

    protected ScriptSignature(final Signature signature, final HashType hashType, final ByteArray extraBytes) {
        _signature = signature;
        _hashType = hashType;
        _extraBytes = extraBytes;
    }

    public ScriptSignature(final Signature signature, final HashType hashType) {
        _signature = signature;
        _hashType = hashType;
        _extraBytes = NO_EXTRA_BYTES;
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

    public Boolean isEmpty() {
        return _signature.isEmpty();
    }

    public Boolean hasExtraBytes() {
        return (! _extraBytes.isEmpty());
    }

    public ByteArray getExtraBytes() {
        return _extraBytes;
    }
}
