package com.softwareverde.bitcoin.type.key;

import com.softwareverde.bitcoin.secp256k1.Secp256k1;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.constable.bytearray.ImmutableByteArray;
import com.softwareverde.util.HexUtil;
import com.softwareverde.util.Util;

import java.security.SecureRandom;

public class PrivateKey extends ImmutableByteArray {
    public static final Integer KEY_BYTE_COUNT = (256 / 8);

    public static PrivateKey createNewKey() {
        final SecureRandom secureRandom = new SecureRandom();

        final PrivateKey privateKey = new PrivateKey();
        secureRandom.nextBytes(privateKey._bytes);
        return privateKey;
    }

    public static PrivateKey parseFromHexString(final String hexString) {
        final PrivateKey privateKey = new PrivateKey();
        final byte[] decodedPrivateKeyData = HexUtil.hexStringToByteArray(hexString);
        if ((decodedPrivateKeyData == null) || (decodedPrivateKeyData.length != KEY_BYTE_COUNT)) { return null; }

        for (int i=0; i<KEY_BYTE_COUNT; ++i) {
            privateKey._bytes[i] = decodedPrivateKeyData[i];
        }

        return privateKey;
    }

    protected byte[] _getPublicKeyPoint() {
        return Secp256k1.getPublicKeyPoint(_bytes);
    }

    protected PrivateKey() {
        super(new byte[KEY_BYTE_COUNT]);
    }

    protected PrivateKey(final byte[] bytes) {
        super(new byte[KEY_BYTE_COUNT]);
        ByteUtil.setBytes(_bytes, bytes);
    }

    public PublicKey getPublicKey() {
        final byte[] publicKeyPoint = _getPublicKeyPoint();
        return new PublicKey(publicKeyPoint);
    }

    @Override
    public byte getByte(final int index) {
        return _bytes[index];
    }

    @Override
    public byte[] getBytes(final int index, final int byteCount) {
        return ByteUtil.copyBytes(_bytes, index, byteCount);
    }

    @Override
    public byte[] getBytes() {
        return Util.copyArray(_bytes);
    }

    @Override
    public int getByteCount() {
        return _bytes.length;
    }

    @Override
    public boolean isEmpty() {
        return (_bytes.length > 0);
    }

    @Override
    public ImmutableByteArray asConst() {
        return this;
    }

    @Override
    public String toString() {
        return HexUtil.toHexString(_bytes);
    }
}
