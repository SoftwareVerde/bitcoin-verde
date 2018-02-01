package com.softwareverde.bitcoin;

import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.util.Util;

import java.security.SecureRandom;

public class PrivateKey {
    public static final Integer KEY_BIT_COUNT = 256;
    public static final Integer KEY_BYTE_COUNT = (KEY_BIT_COUNT / 8);

    public static PrivateKey createNewKey() {
        final SecureRandom secureRandom = new SecureRandom();

        final PrivateKey privateKey = new PrivateKey();
        secureRandom.nextBytes(privateKey._data);
        return privateKey;
    }

    private final byte[] _data = new byte[KEY_BYTE_COUNT];

    public byte[] getBytes() {
        return Util.copyArray(_data);
    }

    @Override
    public String toString() {
        return BitcoinUtil.toHexString(_data);
    }
}
