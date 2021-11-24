package com.softwareverde.bitcoin.util;

import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.bitcoin.address.AddressInflater;
import com.softwareverde.bitcoin.secp256k1.signature.BitcoinMessageSignature;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.cryptography.secp256k1.Secp256k1;
import com.softwareverde.cryptography.secp256k1.key.PrivateKey;
import com.softwareverde.cryptography.secp256k1.key.PublicKey;
import com.softwareverde.cryptography.secp256k1.signature.Secp256k1Signature;
import com.softwareverde.cryptography.util.HashUtil;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.Base32Util;
import com.softwareverde.util.Base58Util;
import com.softwareverde.util.Util;
import com.softwareverde.util.bytearray.ByteArrayBuilder;

import java.nio.charset.StandardCharsets;

public class BitcoinUtil {
    public static final String BITCOIN_SIGNATURE_MESSAGE_MAGIC = "Bitcoin Signed Message:\n";

    protected static Sha256Hash _getBitcoinMessagePreImage(final String message) {
        final String preamble = BITCOIN_SIGNATURE_MESSAGE_MAGIC;
        final byte[] preambleBytes = preamble.getBytes(StandardCharsets.UTF_8);
        final byte[] messageBytes = message.getBytes(StandardCharsets.UTF_8);
        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
        byteArrayBuilder.appendByte((byte) (preambleBytes.length & 0xFF));
        byteArrayBuilder.appendBytes(preambleBytes);
        byteArrayBuilder.appendBytes(ByteUtil.variableLengthIntegerToBytes(messageBytes.length));
        byteArrayBuilder.appendBytes(messageBytes);
        return HashUtil.doubleSha256(MutableByteArray.wrap(byteArrayBuilder.build()));
    }

    public static String toBase58String(final byte[] bytes) {
        return Base58Util.toBase58String(bytes);
    }

    public static byte[] base58StringToBytes(final String base58String) {
        return Base58Util.base58StringToByteArray(base58String);
    }

    public static String toBase32String(final byte[] bytes) {
        return Base32Util.toBase32String(bytes);
    }

    public static byte[] base32StringToBytes(final String base58String) {
        return Base32Util.base32StringToByteArray(base58String);
    }

    public static String reverseEndianString(final String string) {
        final int charCount = string.length();
        final char[] reverseArray = new char[charCount];
        for (int i = 0; i < (charCount / 2); ++i) {
            int index = (charCount - (i * 2)) - 1;
            reverseArray[i * 2] = string.charAt(index - 1);
            reverseArray[(i * 2) + 1] = string.charAt(index);
        }
        return new String(reverseArray);
    }

    public static BitcoinMessageSignature signBitcoinMessage(final PrivateKey privateKey, final String message, final Boolean useCompressedAddress) {
        final PublicKey publicKey = privateKey.getPublicKey();
        final Sha256Hash preImage = _getBitcoinMessagePreImage(message);
        final Secp256k1Signature signature = Secp256k1.sign(privateKey, preImage.getBytes());

        int recoveryId = -1;
        for (int candidateRecoveryId = 0; candidateRecoveryId < 4; candidateRecoveryId++) {
            final PublicKey publicKeyUsedForSigning = PublicKey.fromSignature(signature, preImage, candidateRecoveryId);
            if (publicKeyUsedForSigning == null) { continue; }
            if (! Util.areEqual(publicKey, publicKeyUsedForSigning)) { continue; }

            recoveryId = candidateRecoveryId;
            break;
        }
        if (recoveryId < 0) { return null; }

        return BitcoinMessageSignature.fromSignature(signature, recoveryId, useCompressedAddress);
    }

    public static Boolean verifyBitcoinMessage(final String message, final Address address, final BitcoinMessageSignature bitcoinMessageSignature) {
        final AddressInflater addressInflater = new AddressInflater();

        final Sha256Hash preImage = _getBitcoinMessagePreImage(message);
        final Boolean isCompressedAddress = bitcoinMessageSignature.isCompressedAddress();
        final int recoveryId = bitcoinMessageSignature.getRecoveryId();

        final Secp256k1Signature secp256k1Signature = bitcoinMessageSignature.getSignature();
        final PublicKey publicKeyUsedForSigning = PublicKey.fromSignature(secp256k1Signature, preImage, recoveryId);
        if (publicKeyUsedForSigning == null) { return false; }

        final Address publicKeyAddress = addressInflater.fromPublicKey(publicKeyUsedForSigning, isCompressedAddress);
        if (! Util.areEqual(address, publicKeyAddress)) { return false; }

        return Secp256k1.verifySignature(secp256k1Signature, publicKeyUsedForSigning, preImage.getBytes());
    }

    /**
     * Returns the Log (base2) of x, rounded down.
     *  Ex: log2(65280) -> 15 (Mathematically this value is 15.99...)
     */
    public static int log2(final int value) {
        return (31 - Integer.numberOfLeadingZeros(value));
    }

    /**
     * Returns the Log (base2) of x, rounded down.
     *  Ex: log2(65280) -> 15 (Mathematically this value is 15.99...)
     */
    public static int log2(final long value) {
        return (63 - Long.numberOfLeadingZeros(value));
    }

    public static void exitFailure() {
        Logger.flush();
        System.exit(1);
    }

    public static void exitSuccess() {
        Logger.flush();
        System.exit(0);
    }

    protected BitcoinUtil() { }

}
