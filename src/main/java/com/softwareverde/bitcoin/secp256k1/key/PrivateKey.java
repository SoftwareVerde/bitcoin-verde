package com.softwareverde.bitcoin.secp256k1.key;

import com.softwareverde.bitcoin.secp256k1.Secp256k1;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.ImmutableByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.util.ByteUtil;
import com.softwareverde.util.HexUtil;
import com.softwareverde.util.Util;

import java.security.SecureRandom;

public class PrivateKey extends ImmutableByteArray {
    public static final Integer KEY_BYTE_COUNT = (256 / 8);

    protected static final ByteArray MAX_PRIVATE_KEY = new ImmutableByteArray(HexUtil.hexStringToByteArray("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364140"));
    protected static final ByteArray ZERO = new MutableByteArray(KEY_BYTE_COUNT).asConst();

    /**
     * Return true is provide byte array, when interpreted as an unsigned integer is between the bounds listed
     * <a href="https://en.bitcoin.it/wiki/Private_key#Range_of_valid_ECDSA_private_keys">here</a>.
     */
    private static Boolean _isValidPrivateKey(final ByteArray bytes) {
        if (! Util.areEqual(KEY_BYTE_COUNT, bytes.getByteCount())) {
            return false;
        }

        // 0 is not valid...
        if (Util.areEqual(bytes, ZERO)) {
            return false;
        }

        // Must be less than MAX_PRIVATE_KEY...
        for (int i = 0; i < bytes.getByteCount(); ++i) {
            final int maxValueByte = ByteUtil.byteToInteger(MAX_PRIVATE_KEY.getByte(i));
            final int targetByte = ByteUtil.byteToInteger(bytes.getByte(i));
            if (targetByte == maxValueByte) { continue; }
            return (targetByte < maxValueByte);
        }

        return true;
    }

    public static PrivateKey createNewKey() {
        final SecureRandom secureRandom = new SecureRandom();

        final PrivateKey privateKey = new PrivateKey();

        do {
            secureRandom.nextBytes(privateKey._bytes);
        }
        while (! _isValidPrivateKey(privateKey));

        return privateKey;
    }

    public static PrivateKey fromHexString(final String hexString) {
        final byte[] decodedPrivateKeyData = HexUtil.hexStringToByteArray(hexString);
        if ((decodedPrivateKeyData == null) || (decodedPrivateKeyData.length != KEY_BYTE_COUNT)) { return null; }

        final ByteArray keyBytes = MutableByteArray.wrap(decodedPrivateKeyData);
        if (! _isValidPrivateKey(keyBytes)) { return null; }

        return new PrivateKey(keyBytes);
    }

    public static PrivateKey fromBytes(final ByteArray keyBytes) {
        if (! _isValidPrivateKey(keyBytes)) { return null; }

        return new PrivateKey(keyBytes);
    }

    protected PrivateKey() {
        super(new byte[KEY_BYTE_COUNT]);
    }

    protected PrivateKey(final ByteArray bytes) {
        super(new byte[KEY_BYTE_COUNT]);
        for (int i = 0; i < _bytes.length; ++i) {
            _bytes[i] = bytes.getByte(i);
        }
    }

    public PublicKey getPublicKey() {
        final byte[] publicKeyPointBytes = Secp256k1.getPublicKeyPoint(_bytes);
        if (publicKeyPointBytes == null) { return null; }

        final ByteArray publicKeyPoint = MutableByteArray.wrap(publicKeyPointBytes);
        return PublicKey.fromBytes(publicKeyPoint);
    }

    @Override
    public PrivateKey asConst() {
        return this;
    }

    @Override
    protected void finalize() throws Throwable {
        ByteUtil.cleanByteArray(_bytes);
        super.finalize();
    }
}
