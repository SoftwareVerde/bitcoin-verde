package com.softwareverde.bitcoin.secp256k1;

import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.bitcoin.address.AddressInflater;
import com.softwareverde.bitcoin.jni.NativeSecp256k1;
import com.softwareverde.bitcoin.secp256k1.signature.BitcoinMessageSignature;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.bitcoin.util.Util;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.logging.Logger;
import com.softwareverde.security.hash.sha256.Sha256Hash;
import com.softwareverde.security.secp256k1.key.PrivateKey;
import com.softwareverde.security.secp256k1.key.PublicKey;
import com.softwareverde.security.secp256k1.signature.Secp256k1Signature;
import com.softwareverde.security.secp256k1.signature.Signature;
import com.softwareverde.security.util.HashUtil;
import com.softwareverde.util.Container;
import com.softwareverde.util.bytearray.ByteArrayBuilder;

import java.nio.charset.StandardCharsets;

public class Secp256k1 extends com.softwareverde.security.secp256k1.Secp256k1 {

    protected static Sha256Hash _getBitcoinMessagePreImage(final String message) {
        final String preamble = "Bitcoin Signed Message:\n";
        final byte[] preambleBytes = preamble.getBytes(StandardCharsets.UTF_8);
        final byte[] messageBytes = message.getBytes(StandardCharsets.UTF_8);
        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
        byteArrayBuilder.appendByte((byte) (preambleBytes.length & 0xFF));
        byteArrayBuilder.appendBytes(preambleBytes);
        byteArrayBuilder.appendBytes(ByteUtil.variableLengthIntegerToBytes(messageBytes.length));
        byteArrayBuilder.appendBytes(messageBytes);
        return HashUtil.doubleSha256(MutableByteArray.wrap(byteArrayBuilder.build()));
    }

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


    public static BitcoinMessageSignature signBitcoinMessage(final PrivateKey privateKey, final String message, final Boolean useCompressedAddress) {
        final PublicKey publicKey = privateKey.getPublicKey();
        final Sha256Hash preImage = _getBitcoinMessagePreImage(message);
        final Secp256k1Signature signature = com.softwareverde.security.secp256k1.Secp256k1.sign(privateKey, preImage.getBytes());

        boolean signatureSuccessful = false;
        final Container<Integer> recoveryId = new Container<Integer>(-1);
        for (int i = 0; i < 4; ++i) {
            if (recoveryId.value >= 4) { break; } // PublicKey::fromSignature may also update the recoveryId...
            recoveryId.value = Math.max(i, (recoveryId.value + 1));

            final PublicKey publicKeyUsedForSigning = PublicKey.fromSignature(signature, preImage, recoveryId);
            if (publicKeyUsedForSigning == null) { continue; }

            if (Util.areEqual(publicKey, publicKeyUsedForSigning)) {
                signatureSuccessful = true;
                break;
            }
        }
        if (! signatureSuccessful) { return null; }

        return BitcoinMessageSignature.fromSignature(signature, recoveryId.value, useCompressedAddress);
    }

    public static Boolean verifyBitcoinMessage(final String message, final Address address, final BitcoinMessageSignature bitcoinMessageSignature) {
        final AddressInflater addressInflater = new AddressInflater();
        final Container<Integer> recoveryId = new Container<Integer>();

        final Sha256Hash preImage = _getBitcoinMessagePreImage(message);
        final Boolean isCompressedAddress = bitcoinMessageSignature.isCompressedAddress();
        recoveryId.value = bitcoinMessageSignature.getRecoveryId();

        final Secp256k1Signature secp256k1Signature = bitcoinMessageSignature.getSignature();
        final PublicKey publicKeyUsedForSigning = PublicKey.fromSignature(secp256k1Signature, preImage, recoveryId);
        if (publicKeyUsedForSigning == null) { return false; }
        if (! Util.areEqual(bitcoinMessageSignature.getRecoveryId(), recoveryId.value)) { return false; } // The provided recoveryId was incorrect.

        final Address publicKeyAddress = (isCompressedAddress ? addressInflater.compressedFromPublicKey(publicKeyUsedForSigning) : addressInflater.fromPublicKey(publicKeyUsedForSigning));
        return Util.areEqual(address, publicKeyAddress);
    }

    protected Secp256k1() { }
}
